package com.itzixi.curator.utils;

/**
 * 
 * @Title: ZKLockTypeEnum.java
 * @Package com.itzixi.curator.utils
 * @Description: 分布式锁类型
 * Copyright: Copyright (c) 2017
 * Company:FURUIBOKE.SCIENCE.AND.TECHNOLOGY
 * 
 * @author leechenxiang
 * @date 2017年11月23日 下午1:34:59
 * @version V1.0
 */
public enum ZKLockTypeEnum {
	
	READ(0),			// 读锁
	WRITE(1);			// 写锁
	
	public final int value;
	
	ZKLockTypeEnum(int value) {
		this.value = value;
	}
	
	public int getValue() {
		return value;
	}
	
}
