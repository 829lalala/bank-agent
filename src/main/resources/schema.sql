-- PGVector 向量库建表脚本（spring.sql.init 在应用启动时执行，IF NOT EXISTS 保证幂等）。
-- 表结构必须与 PgVectorStore（spring-ai-pgvector-store 2.0.1）的读写 SQL 精确匹配——
-- 模板提取自该类字节码常量。Spring AI 2.0 不再自动建表（schema 交由用户管理，
-- 生产环境建议用 Flyway/Liquibase 托管本脚本）。

-- pgvector 扩展本体 + id 默认值所需的两个配套扩展
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 向量表：dimensions 必须与 application.yml 的
-- spring.ai.vectorstore.pgvector.dimensions 一致（智谱 embedding-3 = 2048）
CREATE TABLE IF NOT EXISTS vector_store (
	id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
	content text,
	metadata json,
	embedding vector(2048)
);

-- 检索索引说明：pgvector 的 HNSW/IVFFlat 索引维度上限为 2000，而 embedding-3 是
-- 2048 维，超限无法建索引（实测报错 column cannot have more than 2000 dimensions）。
-- 当前知识块数量级小（十~百块），不建索引时检索退化为全表精确扫描，性能无感；
-- 数据量大后的两个演进方向：①智谱 embedding-3 支持 dimensions=1024，降维换索引
-- （需重新摄取并跑评估集回归）；②迁移专用向量库（Milvus/Qdrant）。
-- CREATE INDEX IF NOT EXISTS vector_store_hnsw_idx ON vector_store USING hnsw (embedding vector_cosine_ops);
