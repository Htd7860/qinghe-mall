"""One-time local bootstrap; user runs it, no default password and no secret SQL file.
Requires Python bcrypt and MySQL client login-path qinghe-local.
The assistant has not run this script. It is not a test or public HTTP bootstrap endpoint.
"""
import argparse
import getpass
import json
import re
import shutil
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description="Initialize the first platform admin in local qinghe_system")
    parser.add_argument("--username", required=True)
    parser.add_argument("--mysql", default=shutil.which("mysql"))
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9]{4,30}", args.username) or not args.mysql:
        raise SystemExit("Require a 4-30 alphanumeric username and a MySQL client path.")
    try:
        import bcrypt
    except ImportError:
        raise SystemExit("Install bcrypt in your chosen Python environment first: python -m pip install bcrypt")
    client = [args.mysql, "--login-path=qinghe-local", "--host=127.0.0.1", "--port=3306",
              "--database=qinghe_system", "--batch", "--skip-column-names", "--skip-reconnect",
              "--default-character-set=utf8mb4"]

    def run(sql):
        result = subprocess.run(client, input=sql, text=True, encoding="utf-8", capture_output=True)
        if result.returncode:
            # Client diagnostics can contain statements/parameters. Never print or save them.
            raise SystemExit("Database operation not confirmed. No secret diagnostics were printed. Inspect state before retrying.")
        return result.stdout

    environment = run("SELECT DATABASE(),@@hostname,@@port,VERSION();")
    values = environment.strip().split("\t")
    minor = re.match(r"8\.0\.(\d+)", values[3]) if len(values) == 4 else None
    if len(values) != 4 or values[:3] != ["qinghe_system", "QIANG", "3306"] or not minor or int(minor.group(1)) < 16:
        raise SystemExit("This helper only initializes the explicitly documented local QIANG/qinghe_system MySQL 8.0 instance.")
    catalog = json.loads(Path(__file__).with_name("management-resource-catalog.json").read_text(encoding="utf-8"))
    platform = [row for row in catalog if row["realm"] == "platform"]
    ids = ",".join(str(row["id"]) for row in platform)
    # Verify the expected catalog shape, rather than granting every future platform resource by realm.
    conditions = []
    for row in platform:
        conditions.append("(id={id} AND realm='platform' AND type={type} AND parent_id={parentId} AND permission='{permission}' AND path='{path}')".format(**row))
    catalog_condition = " OR ".join(conditions)
    state = run("SELECT (SELECT COUNT(*) FROM system_users WHERE tenant_id=0),"
                "(SELECT COUNT(*) FROM system_role WHERE tenant_id=0),"
                "(SELECT COUNT(*) FROM system_management_mutex WHERE id=1 AND platform_admin_user_id IS NULL AND platform_admin_role_id IS NULL),"
                f"(SELECT COUNT(*) FROM system_menu WHERE deleted=b'0' AND status=0 AND ({catalog_condition}));")
    if state.strip() != f"0\t0\t1\t{len(platform)}":
        raise SystemExit("Platform identity already exists or catalog/reference preflight differs. Refuse to replace existing data.")
    password = getpass.getpass("New platform password (not echoed): ")
    confirmation = getpass.getpass("Repeat password: ")
    raw = password.encode("utf-8")
    if password != confirmation or len(password) < 8 or not password.strip() or len(raw) > 72 or any(ord(ch)<32 or ord(ch)==127 for ch in password):
        raise SystemExit("Passwords must match, contain at least 8 characters, at most 72 UTF-8 bytes and no control characters.")
    hashed = bcrypt.hashpw(raw, bcrypt.gensalt(rounds=10)).decode("ascii")
    del password, confirmation, raw
    if not re.fullmatch(r"\$2[aby]\$10\$[./A-Za-z0-9]{53}", hashed):
        raise SystemExit("Unexpected bcrypt output.")
    sql = f"""
CREATE TEMPORARY TABLE bootstrap_guard (ok tinyint NOT NULL CHECK (ok=1)) ENGINE=InnoDB;
START TRANSACTION;
SELECT id FROM system_management_mutex WHERE id=1 FOR UPDATE;
INSERT INTO bootstrap_guard VALUES ((SELECT COUNT(*) FROM system_management_mutex WHERE id=1 AND platform_admin_user_id IS NULL AND platform_admin_role_id IS NULL));
INSERT INTO bootstrap_guard VALUES ((SELECT COUNT(*)=0 FROM system_users WHERE tenant_id=0));
INSERT INTO bootstrap_guard VALUES ((SELECT COUNT(*)=0 FROM system_role WHERE tenant_id=0));
INSERT INTO bootstrap_guard VALUES ((SELECT COUNT(*)={len(platform)} FROM system_menu WHERE deleted=b'0' AND status=0 AND ({catalog_condition})));
INSERT INTO system_users (tenant_id,username,password,nickname,status,post_ids,auth_version,credential_version,creator,updater,create_time,update_time,deleted)
VALUES (0,'{args.username}','{hashed}','平台管理员',0,JSON_ARRAY(),0,0,'platform-bootstrap','platform-bootstrap',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),b'0');
SET @new_user_id=LAST_INSERT_ID();
INSERT INTO system_role (tenant_id,name,code,sort,status,type,remark,data_scope,data_scope_dept_ids,auth_version,creator,updater,create_time,update_time,deleted)
VALUES (0,'平台管理员','platform_admin',0,0,1,'首次本地初始化，权限逐资源明确授予',1,JSON_ARRAY(),0,'platform-bootstrap','platform-bootstrap',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),b'0');
SET @new_role_id=LAST_INSERT_ID();
INSERT INTO system_user_role (tenant_id,user_id,role_id,creator,updater,create_time,update_time,deleted)
VALUES (0,@new_user_id,@new_role_id,'platform-bootstrap','platform-bootstrap',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),b'0');
INSERT INTO system_role_menu (tenant_id,role_id,menu_id,creator,updater,create_time,update_time,deleted)
SELECT 0,@new_role_id,id,'platform-bootstrap','platform-bootstrap',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),b'0' FROM system_menu WHERE id IN ({ids});
UPDATE system_management_mutex SET platform_admin_user_id=@new_user_id,platform_admin_role_id=@new_role_id WHERE id=1 AND platform_admin_user_id IS NULL AND platform_admin_role_id IS NULL;
INSERT INTO bootstrap_guard VALUES (ROW_COUNT());
INSERT INTO system_management_audit (tenant_id,actor_tenant_id,actor_user_id,action,target_id,from_version,to_version,details,trace_id,create_time)
VALUES (0,0,@new_user_id,'USER.CREATE',@new_user_id,-1,0,JSON_OBJECT('firstPlatformBootstrap',true),'',CURRENT_TIMESTAMP(6)),
(0,0,@new_user_id,'ROLE.CREATE',@new_role_id,-1,0,JSON_OBJECT('firstPlatformBootstrap',true,'explicitMenuCount',{len(platform)}),'',CURRENT_TIMESTAMP(6));
COMMIT;
SELECT CONCAT('BOOTSTRAP_OK user=',@new_user_id,' role=',@new_role_id);
"""
    output = run(sql)
    del hashed, sql
    if "BOOTSTRAP_OK" not in output:
        raise SystemExit("Result not confirmed; inspect the configured admin reference before retrying.")
    print(next(line for line in output.splitlines() if line.startswith("BOOTSTRAP_OK")))
    print("No token was issued. Start the application yourself and log in through the platform entry.")


if __name__ == "__main__":
    main()
