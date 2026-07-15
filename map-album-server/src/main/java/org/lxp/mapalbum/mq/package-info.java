/**
 * 消息队列消费者（KSHG 规范 §3.2 异步处理；技术方案 §8.1 Kafka 图片处理流水线）。
 *
 * <p>{@code photo.upload} topic 消费者：缩略图生成 / EXIF 提取 / 内容审核。
 *
 * @author lxp
 */
package org.lxp.mapalbum.mq;
