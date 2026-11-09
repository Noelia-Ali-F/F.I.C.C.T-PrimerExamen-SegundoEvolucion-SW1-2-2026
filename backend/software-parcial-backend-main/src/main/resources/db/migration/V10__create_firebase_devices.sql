CREATE TABLE firebase_devices (
    fid VARCHAR(256) PRIMARY KEY,
    username VARCHAR(180) NOT NULL REFERENCES users(username) ON DELETE CASCADE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_firebase_device_user ON firebase_devices(username);
CREATE TABLE firebase_push_deliveries (
    event_id VARCHAR(160) NOT NULL,
    fid VARCHAR(256) NOT NULL REFERENCES firebase_devices(fid) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(event_id,fid)
);
