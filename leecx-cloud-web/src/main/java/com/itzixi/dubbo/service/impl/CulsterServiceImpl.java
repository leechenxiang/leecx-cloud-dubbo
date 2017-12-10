package com.itzixi.dubbo.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itzixi.curator.utils.ZKLockWebUtil;
import com.itzixi.dubbo.service.CulsterService;
import com.itzixi.items.service.ItemsService;
import com.itzixi.orders.service.OrdersService;

@Service
public class CulsterServiceImpl implements CulsterService {

	final static Logger log = LoggerFactory.getLogger(CulsterServiceImpl.class);
	
	@Autowired
	private ZKLockWebUtil zkLockWebUtil;

	@Autowired
	private ItemsService itemsService;
	
	@Autowired
	private OrdersService ordersService;
	
	@Override
//	@TxTransaction
    @Transactional
	public boolean display(String itemId) {
		
		zkLockWebUtil.getXLock();
		
		int buyCounts = 5;
		
		// 1. 判断库存
		int stockCounts = itemsService.getItemCounts(itemId);
		if (stockCounts < buyCounts) {
			zkLockWebUtil.releaseXLock();
			log.info("库存剩余{}件，用户需求量{}件，库存不足，订单创建失败...", stockCounts, buyCounts);
			
			return false;
		}
		
		// 2. 创建订单
		boolean isOrderCreated = ordersService.createOrder(itemId);
		
		// 3. 创建订单成功后，扣除库存
		if (isOrderCreated) {
			log.info("订单创建成功...");
			itemsService.displayReduceCounts(itemId, buyCounts);
		} else {
			log.info("订单创建失败...");
			return false;
		}
		
		int a = 1 / 0;
		
		zkLockWebUtil.releaseXLock();
		
		return true;
	}

}
