package com.itzixi.orders.service;

import com.itzixi.orders.pojo.Orders;

public interface OrdersService {

	public Orders getOrder(String orderId);
	
	/**
	 * 
	 * @Title: OrdersService.java
	 * @Package com.itzixi.orders.service
	 * @Description: 下订单
	 * Copyright: Copyright (c) 2017
	 * Company:FURUIBOKE.SCIENCE.AND.TECHNOLOGY
	 * 
	 * @author leechenxiang
	 * @date 2017年12月8日 上午11:10:27
	 * @version V1.0
	 */
	public boolean createOrder(String itemId);

}

