-- 通知偏好表：普通用户客户端设置页的站内通知开关
CREATE TABLE IF NOT EXISTS t_notification_preference (
    user_id BIGINT PRIMARY KEY REFERENCES t_user(id),
    progress_notify BOOLEAN NOT NULL DEFAULT TRUE,
    evaluation_notify BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
