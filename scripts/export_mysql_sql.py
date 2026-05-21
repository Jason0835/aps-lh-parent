#!/usr/bin/env python3
import pymysql
from pymysql.cursors import SSCursor
from pymysql.err import OperationalError, ProgrammingError
import configparser
import sys

# ====================== 【必须修改】需要导出的表名列表 ======================
TABLE_NAMES = [
    "T_LH_MOULD_CLEAN_PLAN",
    "T_LH_MACHINE_INFO",
    "T_LH_SPECIAL_MATERIAL_BOM",
    "T_LH_MOULD_CHANGE_PLAN",
    "T_LH_PARAMS",
    "T_LH_SCHEDULE_PROCESS_LOG",
    "T_LH_SCHEDULE_RESULT",
    "T_LH_SHIFT_CONFIG",
    "T_LH_DAY_FINISH_QTY",
    "T_LH_SCHE_FINISH_QTY",
    "T_LH_SPECIFY_MACHINE",
    "T_LH_UNSCHEDULED_RESULT",
    "T_MDM_DEV_MAINTENANCE_PLAN",
    "T_MDM_DEVICE_PLAN_SHUT",
    "T_LH_REPAIR_CAPSULE",
    "T_MDM_MATERIAL_INFO",
    "T_MDM_MONTH_SURPLUS",
    "T_MDM_SKU_LH_CAPACITY",
    "T_MDM_SKU_MOULD_REL",
    "T_MDM_WORK_CALENDAR",
    "T_MP_MONTH_PLAN_PROD_FINAL",
    "T_MP_PROC_VERSION",
    "T_MDM_MOULD_INFO",
    "T_CX_STOCK",
    "T_MDM_MATERIAL_CONSUME_DETAIL",
    "T_MDM_SPECIAL_MATERIAL_RECORD",
    "T_LH_PRECISION_PLAN",
    "T_MP_ADJUST_RESULT",
    "T_MDM_SKU_CONSTRUCTION_REF",
    "T_MDM_CAPSULE_CHUCK",
    "T_LH_MACHINE_ONLINE_INFO"
]

# ====================== 导出文件配置 ======================
OUTPUT_SQL_FILE = "table_structure_data.sql"  # 输出的SQL文件名

# ====================== 读取数据库配置文件 ======================
def load_db_config(config_file, section="dev"):
    if not config_file:
        print("❌ 未指定数据库配置文件，请使用 --config 参数指定")
        sys.exit(1)
    config = configparser.RawConfigParser()
    config.read(config_file, encoding="utf-8")
    if section not in config.sections():
        print(f"❌ 配置文件中缺少 [{section}] 节: {config_file}")
        sys.exit(1)
    db_config = {
        "host": config.get(section, "host"),
        "port": config.getint(section, "port"),
        "user": config.get(section, "user"),
        "password": config.get(section, "password"),
        "database": config.get(section, "database"),
        "charset": config.get(section, "charset", fallback="utf8mb4")
    }
    if config.has_option(section, "max_allowed_packet"):
        db_config["max_allowed_packet"] = config.getint(section, "max_allowed_packet")
    return db_config

# ====================== 将一行数据转为VALUES字符串 ======================
def row_to_values(row, columns):
    values = []
    for val in row:
        if val is None:
            values.append("NULL")
        elif isinstance(val, (int, float)):
            values.append(str(val))
        else:
            val_str = str(val).replace("'", "''")
            values.append(f"'{val_str}'")
    return f"({', '.join(values)})"

# ====================== 导出指定表的数据（流式写入） ======================
def export_table_data(cursor, f, table, columns, batch_size):
    # 使用服务端游标逐行读取，避免大表撑爆内存
    cursor.execute(f"SELECT * FROM `{table}`")

    total_rows = 0
    batch_rows = []
    for row in cursor:
        batch_rows.append(row_to_values(row, columns))
        total_rows += 1

        # 达到批次大小，写入一个INSERT
        if len(batch_rows) >= batch_size:
            f.write(f"INSERT INTO `{table}` ({', '.join([f'`{col}`' for col in columns])}) VALUES \n")
            f.write(",\n".join(batch_rows))
            f.write(";\n")
            batch_rows = []

    # 写入剩余的不足一批的行
    if batch_rows:
        f.write(f"INSERT INTO `{table}` ({', '.join([f'`{col}`' for col in columns])}) VALUES \n")
        f.write(",\n".join(batch_rows))
        f.write(";\n")

    return total_rows

# ====================== 核心导出函数 ======================
def export_mysql_table_sql(db_config, batch_size=500):
    conn = None
    cursor = None
    try:
        # 建表语句使用普通游标，数据导出使用服务端游标
        conn = pymysql.connect(**db_config)
        print(f"✅ 数据库连接成功，开始导出表：{TABLE_NAMES}")

        with open(OUTPUT_SQL_FILE, "w", encoding="utf-8") as f:
            f.write("-- 自动生成：表结构 + 数据导出脚本\n")
            f.write(f"-- 数据库：{db_config['database']}\n")
            f.write(f"-- 兼容MySQL5.6/5.7/8.0，自动修复排序规则\n")
            f.write("SET FOREIGN_KEY_CHECKS=0;\n\n")

            for table in TABLE_NAMES:
                print(f"\n📦 正在处理表：{table}")

                # ----- 1. 建表语句 -----
                with conn.cursor() as ddl_cursor:
                    try:
                        ddl_cursor.execute(f"SHOW CREATE TABLE `{table}`")
                        create_table_sql = ddl_cursor.fetchone()[1]
                        create_table_sql = create_table_sql.replace("utf8mb4_0900_ai_ci", "utf8mb4_general_ci")
                        f.write(f"-- 建表语句：{table}\n")
                        f.write(f"DROP TABLE IF EXISTS `{table}`;\n")
                        f.write(f"{create_table_sql};\n\n")
                    except ProgrammingError:
                        print(f"❌ 表 {table} 不存在，跳过")
                        continue

                # ----- 2. 获取表的实际列名 -----
                with conn.cursor() as col_cursor:
                    col_cursor.execute(f"SELECT * FROM `{table}` LIMIT 0")
                    columns = [col[0] for col in col_cursor.description]

                # ----- 3. 数据导出（流式写入，分批INSERT） -----
                dml_cursor = conn.cursor(SSCursor)
                try:
                    total = export_table_data(dml_cursor, f, table, columns, batch_size)
                    if total == 0:
                        f.write(f"-- {table} 表无数据\n\n")
                        print(f"✅ {table} 表无数据，仅导出结构")
                    else:
                        print(f"✅ {table} 表导出完成，共 {total} 条数据")
                finally:
                    dml_cursor.close()

            f.write("SET FOREIGN_KEY_CHECKS=1;\n")

        print(f"\n🎉 导出完成！SQL文件已保存至：{OUTPUT_SQL_FILE}")

    except OperationalError as e:
        print(f"❌ 数据库连接失败：{e}")
    except Exception as e:
        print(f"❌ 导出失败：{e}")
    finally:
        if cursor:
            cursor.close()
        if conn:
            conn.close()
        print("\n🔌 数据库连接已关闭")

# ====================== 执行脚本 ======================
if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="导出MySQL表结构和数据")
    parser.add_argument("--config", default="db_config.ini", help="数据库配置文件路径，默认 db_config.ini")
    parser.add_argument("--section", default="dev", help="配置节名称，默认 dev")
    parser.add_argument("--batch-size", type=int, default=500, dest="batch_size",
                        help="每个INSERT语句包含的行数，默认 500")
    args = parser.parse_args()

    db_config = load_db_config(args.config, args.section)
    export_mysql_table_sql(db_config, batch_size=args.batch_size)
