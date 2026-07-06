CREATE DATABASE IF NOT EXISTS techmart_db;
CREATE USER IF NOT EXISTS 'techmart_user'@'localhost' IDENTIFIED BY 'techmart_password';
GRANT ALL PRIVILEGES ON techmart_db.* TO 'techmart_user'@'localhost';
FLUSH PRIVILEGES;
