CREATE TABLE analysis_results (
                                  id INT PRIMARY KEY AUTO_INCREMENT,
                                  result_data VARCHAR(255),
                                  vul_total_count INT,
                                  vul_data_reachable_count INT
);



/*
 Navicat MySQL Data Transfer

 Source Server         : mydb
 Source Server Type    : MySQL
 Source Server Version : 80200 (8.2.0)
 Source Host           : localhost:3306
 Source Schema         : local

 Target Server Type    : MySQL
 Target Server Version : 80200 (8.2.0)
 File Encoding         : 65001

 Date: 08/11/2023 10:38:26
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for source_rule
-- ----------------------------
DROP TABLE IF EXISTS `source_rule`;
CREATE TABLE `source_rule` (
                               `id` int NOT NULL AUTO_INCREMENT,
                               `rule_id` varchar(128) DEFAULT NULL,
                               `taint_flags` varchar(128) DEFAULT NULL,
                               `namespace_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
                               `class_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
                               `function_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
                               `tag_implements` tinyint DEFAULT '0',
                               `tag_overrides` tinyint DEFAULT '0',
                               `tag_extends` tinyint DEFAULT '0',
                               `out_arguments` varchar(64) DEFAULT NULL,
                               PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;

