package com.itzixi.dubbo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.itzixi.common.utils.LeeJSONResult;
import com.itzixi.curator.utils.ZKLockWebUtil;
import com.itzixi.dubbo.service.CulsterService;
import com.itzixi.items.pojo.Items;
import com.itzixi.items.service.ItemsService;
import com.itzixi.orders.pojo.Orders;
import com.itzixi.orders.service.OrdersService;

@Controller
@Scope("prototype")
public class PayController {
	
	final static Logger log = LoggerFactory.getLogger(PayController.class);
	
	@Autowired
	private ZKLockWebUtil zkLockWebUtil;

	@Autowired
	private ItemsService itemsService;
	
	@Autowired
	private OrdersService ordersService;
	
	@Autowired
	private CulsterService culsterService;
	
	@RequestMapping("/item")
	@ResponseBody
	public LeeJSONResult getItemById(String id) {
		
		Items item = itemsService.getItem(id);
		
		return LeeJSONResult.ok(item);
	}
	
	@RequestMapping("/order")
	@ResponseBody
	public LeeJSONResult getOrderById(String id) {
		
		Orders order = ordersService.getOrder(id);
		
		return LeeJSONResult.ok(order);
	}
	
	
	// 集群1
	@RequestMapping("/play1")
	@ResponseBody
	public LeeJSONResult play1(String itemId) {
		boolean result = culsterService.display(itemId);
		return LeeJSONResult.ok(result ? "订单创建成功..." : "订单创建失败...");
	}
	
	// 集群2
	@RequestMapping("/play2")
	@ResponseBody
	public LeeJSONResult play2(String itemId) {
		boolean result = culsterService.display(itemId);
		return LeeJSONResult.ok(result ? "订单创建成功..." : "订单创建失败...");
	}
}
