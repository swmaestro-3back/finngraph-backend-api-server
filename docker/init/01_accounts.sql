-- 비밀번호는 로컬 개발 전용이며, 운영에서는 반드시 교체한다.

CREATE ROLE finngraph_migrator WITH LOGIN PASSWORD 'migrator';
CREATE ROLE finngraph_api      WITH LOGIN PASSWORD 'api';

ALTER SCHEMA public OWNER TO finngraph_migrator;
GRANT ALL ON SCHEMA public TO finngraph_migrator;

GRANT CONNECT ON DATABASE finngraph TO finngraph_api;
GRANT USAGE ON SCHEMA public TO finngraph_api;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO finngraph_api;

ALTER DEFAULT PRIVILEGES FOR ROLE finngraph_migrator IN SCHEMA public
    GRANT SELECT ON TABLES TO finngraph_api;
