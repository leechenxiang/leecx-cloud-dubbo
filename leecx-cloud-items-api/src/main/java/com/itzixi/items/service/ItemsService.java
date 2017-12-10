package com.itzixi.items.service;

import com.itzixi.items.pojo.Items;

public interface ItemsService {

	public Items getItem(String itemId);
	
	/**
	 * 
	 * @Title: ItemsService.java
	 * @Package com.itzixi.items.service
	 * @Description: 查询商品库存
	 * Copyright: Copyright (c) 2017
	 * Company:FURUIBOKE.SCIENCE.AND.TECHNOLOGY
	 * 
	 * @author leechenxiang
	 * @date 2017年12月8日 上午11:09:55
	 * @version V1.0
	 */
	public int getItemCounts(String itemId);
	
	/**
	 * 
	 * @Title: ItemsService.java
	 * @Package com.itzixi.items.service
	 * @Description: 购买商品成功后减少库存
	 * Copyright: Copyright (c) 2017
	 * Company:FURUIBOKE.SCIENCE.AND.TECHNOLOGY
	 * 
	 * @author leechenxiang
	 * @date 2017年12月8日 上午11:10:02
	 * @version V1.0
	 */
	public void displayReduceCounts(String itemId, int buyCounts);

}

