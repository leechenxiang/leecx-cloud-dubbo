package com.itzixi.curator.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCache.StartMode;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 
 * @Title: ZKLockUtil.java
 * @Package com.itzixi.curator.utils
 * @Description: 本类不能在同一台电脑上使用，由于static的作用，只能在不同的服务器上做
 * 				   不适用与伪分布式，需要在真实分布式环境下才会生效
 * 				   建议测试的时候一台本地，一台在虚拟机
 * Copyright: Copyright (c) 2017
 * Company:FURUIBOKE.SCIENCE.AND.TECHNOLOGY
 * 
 * @author leechenxiang
 * @date 2017年11月22日 下午1:15:44
 * @version V1.0
 */
@Component
public class ZKLockWebUtil {
	
	private CuratorFramework client = null;		// zk客户端
	
	final static Logger log = LoggerFactory.getLogger(ZKLockWebUtil.class);
	
	// 用于排它锁
	protected static CountDownLatch xLocklatch = new CountDownLatch(1);
	
	// 用于共享锁
	protected static CountDownLatch shareLocklatch = new CountDownLatch(1);
	private static String selfIdentity = null;
	private static String selfNodeName = null;
	
	private static final String READ_OPERATOR = "Read_";
	private static final String WRITE_OPERATOR = "Write_";
	
	// 构造函数
    public ZKLockWebUtil(CuratorFramework client) {
    	this.client = client;
    }
    
    /**
     * 
     * @Title: ZKLockWebUtil.java
     * @Package com.itzixi.curator.utils
     * @Description: 初始化相关锁
     * Copyright: Copyright (c) 2017
     * Company:FURUIBOKE.SCIENCE.AND.TECHNOLOGY
     * 
     * @author leechenxiang
     * @date 2017年11月22日 下午8:32:23
     * @version V1.0
     */
    public void init() {
		
    	// 使用命名空间
    	client = client.usingNamespace("ZKLocks");

		// 创建对应锁的节点
		try {
			if (client.checkExists().forPath("/XLock") == null) {
				client.create().creatingParentsIfNeeded()
						.withMode(CreateMode.PERSISTENT)
						.withACL(Ids.OPEN_ACL_UNSAFE)
						.forPath("/XLock");
			}
			// 创建锁watch事件监听
			addChildWatcher("/XLock");
			
			if (client.checkExists().forPath("/ShareLock") == null) {
				client.create()
						.creatingParentsIfNeeded()
						.withMode(CreateMode.PERSISTENT)
						.withACL(Ids.OPEN_ACL_UNSAFE)
						.forPath("/ShareLock");
			}
		} catch (Exception e) {
			log.error("zookeeper client connecting error... please try again...");
			throw new RuntimeException("zookeeper client connecting error... please try again...");
		}
	}
	
	/**
	 * 
	 * @Title: ZKLockUtil.java
	 * @Package com.itzixi.curator.utils
	 * @Description: 获得排它锁
	 * Copyright: Copyright (c) 2017
	 * Company:FURUIBOKE.SCIENCE.AND.TECHNOLOGY
	 * 
	 * @author leechenxiang
	 * @date 2017年11月22日 下午1:15:58
	 * @version V1.0
	 */
	public void getXLock() {
		// 使用这个死循环，当且仅当获得锁成功后才会跳出循环
		while (true) {
			try {
				client.create()
						.creatingParentsIfNeeded()
						.withMode(CreateMode.EPHEMERAL)		// 分布式锁使用临时节点即可，因为会话失效需要释放锁
						.withACL(Ids.OPEN_ACL_UNSAFE)
						.forPath("/XLock/lock");
				log.info("获得排它锁成功...");
				return;										// 如果节点创建成功，即说明获取锁成功
			} catch (Exception e) {
				log.info("获得排它锁失败...");
				try {
					// 如果没有获取到锁，需要重新设置同步资源值
					if(xLocklatch.getCount() <= 0){
						xLocklatch = new CountDownLatch(1);
					}
					// 阻塞线程
					xLocklatch.await();
				} catch (InterruptedException e1) {
					e1.printStackTrace();
					log.error("", e1);
				}
			}
		}
	}

	/**
	 * 
	 * @Title: ZKLockWebUtil.java
	 * @Package com.itzixi.curator.utils
	 * @Description: 获得共享锁
	 * 					参数：
	 * 						lockType：锁类型，类型都在ZKLockTypeEnum这个枚举类中定义
	 * 						identity：身份，指的是当前锁的归属人。可以用调用的 方法名，或者ip 作为身份都行
	 * Copyright: Copyright (c) 2017
	 * Company:FURUIBOKE.SCIENCE.AND.TECHNOLOGY
	 * 
	 * @author leechenxiang
	 * @date 2017年11月23日 下午1:35:53
	 * @version V1.0
	 */
	public boolean getShareLock(int lockType, String identity) {
		// 对参数进行一些判断
		// 锁拥有者不能为空
		if (StringUtils.isEmpty(identity)) {
			throw new RuntimeException("当前锁的归属人identity不能为空...");
		}
		// 不能包含字符
		if (identity.indexOf("-") != -1) {
			throw new RuntimeException("当前锁的归属人identity不能包含字符-");
		}
		// 判断所类型只能是read或者write
		if (lockType != ZKLockTypeEnum.READ.value && lockType != ZKLockTypeEnum.WRITE.value) {
			throw new RuntimeException("分布式锁类型lockType不正确，只能是 READ 或者 WRITE...");
		}
		
		// 根据不同的锁类型，创建节点名称
		String nodeName = null;
		if (lockType == ZKLockTypeEnum.READ.value) {
			nodeName = READ_OPERATOR + identity + "-";
		} else if (lockType == ZKLockTypeEnum.WRITE.value) {
			nodeName = WRITE_OPERATOR + identity + "-";
		}
		selfIdentity = nodeName;
		try {
			// 创建节点
			selfNodeName = client.create()
								 .creatingParentsIfNeeded()
								 .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
								 .withACL(Ids.OPEN_ACL_UNSAFE)
								 .forPath("/ShareLock/" + nodeName);
			log.info("创建节点:{}", selfNodeName);
			// 获取子节点列表
			List<String> lockChildrens = client.getChildren().forPath("/ShareLock");
			if (!canGetLock(lockChildrens, lockType, nodeName.substring(0, nodeName.length() - 1), false)) {
				log.info("获取共享锁失败...等待中...");
				shareLocklatch.await();
			}
			// return;// 获得锁成功就返回
		} catch (Exception e) {
			log.info("error: {}", e);
			return false;
		}
		
		log.info("获取共享锁成功...");		
		return true;
	}

	/**
	 * 
	 * @Title: ZKLockWebUtil.java
	 * @Package com.itzixi.curator.utils
	 * @Description: 根据子节点列表以及所类型判断当前拥有者是否可以获得锁
	 * 				   参数：
	 * 					childrens：共享锁节点下的子节点
	 * 					lockType：锁类型，类型都在ZKLockTypeEnum这个枚举类中定义
	 * 					identity：锁的拥有者
	 * 					isNeedChildWatch：是否需要添加watch事件通知
	 * Copyright: Copyright (c) 2017
	 * Company:FURUIBOKE.SCIENCE.AND.TECHNOLOGY
	 * 
	 * @author leechenxiang
	 * @date 2017年11月23日 下午1:55:00
	 * @version V1.0
	 */
	private boolean canGetLock(List<String> childrens, int lockType, String identity, boolean isNeedChildWatch) {
		boolean result = false;
		// 如果共享锁下的子节点数量为0，那么直接返回true，表示可以获得锁
		if(childrens.size() <= 0) {
			return true;
		}
		try {
			// 当前节点的顺序，用于记录
			String currentSequen = null;
			// 顺序list
			List<String> sequenList = new ArrayList<String>();
			// 存放顺序和身份的map
			Map<String,String> sequenIdentitysMap = new HashMap<String,String>();
			// 循环解析节点名称, 放入map
			for (String child : childrens) {
				String splits[] = child.split("-");
				sequenList.add(splits[1]);
				sequenIdentitysMap.put(splits[1], splits[0]);
				if (identity.equals(splits[0])) {
					currentSequen = splits[1];
				}
			}

			// 为顺序list排序
			List<String> sortSequens = new ArrayList<String>();
			sortSequens.addAll(sequenList);
			Collections.sort(sortSequens);

			// 第一个节点，则无论是读锁还是写锁都可以获取
			if (currentSequen.equals(sortSequens.get(0))) {
				result = true;
				log.info("请求锁获得锁, 第一个请求锁就是第一个节点，获取锁成功...");
				return result;
			} else {
				// 写锁
				if (lockType == ZKLockTypeEnum.WRITE.value) {
					result = false;
					//第一次请求取锁则设置监听，以后就不设置了，因为监听一直存在
					if(isNeedChildWatch==false) {
						addChildWatcher("/ShareLock");
					}
					log.info("请求写锁，因为前面有其它锁，所以获取锁失败");
					return result;
				}
			}
			
			// 设置是否含有写操作的flag
			boolean hasWrite = true;
			for (String seq : sortSequens) {
				if (seq.equals(currentSequen)) {
					break;
				}
				if (!sequenIdentitysMap.get(seq).startsWith(WRITE_OPERATOR)) {
					hasWrite = false;
				}
			}
			
			// 如果是当前是读操作，并且子节点中也都是读操作，那么返回true，表示后面的线程可以获得锁
			// 如果是当前是读操作，子节点中含有写操作，那么返回false，表示后面的线程不能获得锁
			if (lockType == ZKLockTypeEnum.READ.value && hasWrite == false) {
				result = true;
			} else if (lockType == ZKLockTypeEnum.READ.value && hasWrite == true) {
				result = false;
			}
			// 如果不能获得锁，则为这些子节点添加watch事件监听
			if (result == false) {
				// 添加监听
				addChildWatcher("/ShareLock");
				log.info("线程无法获取锁，为{}的子节点添加锁的监听器成功...", "/ShareLock");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * 
	 * @Title: ZKLockWebUtil.java
	 * @Package com.itzixi.curator.utils
	 * @Description: 释放排它锁
	 * Copyright: Copyright (c) 2017
	 * Company:FURUIBOKE.SCIENCE.AND.TECHNOLOGY
	 * 
	 * @author leechenxiang
	 * @date 2017年11月23日 下午1:59:10
	 * @version V1.0
	 */
	public boolean releaseXLock() {
		try {
			if (client.checkExists().forPath("/XLock/lock") != null) {
				client.delete().forPath("/XLock/lock");
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	/**
	 * 
	 * @Title: ZKLockWebUtil.java
	 * @Package com.itzixi.curator.utils
	 * @Description: 释放共享锁
	 * Copyright: Copyright (c) 2017
	 * Company:FURUIBOKE.SCIENCE.AND.TECHNOLOGY
	 * 
	 * @author leechenxiang
	 * @date 2017年11月23日 下午1:59:28
	 * @version V1.0
	 */
	public boolean releaseShareLock() {
		try {
			if (client.checkExists().forPath(selfNodeName) != null) {
				client.delete().forPath(selfNodeName);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	/**
	 * 
	 * @Title: ZKLockUtil.java
	 * @Package com.itzixi.curator.utils
	 * @Description: 为当前节点的子节点创建事件监听
	 * Copyright: Copyright (c) 2017
	 * Company:FURUIBOKE.SCIENCE.AND.TECHNOLOGY
	 * 
	 * @author leechenxiang
	 * @date 2017年11月22日 下午1:27:07
	 * @version V1.0
	 */
	public void addChildWatcher(String path) throws Exception {
		final PathChildrenCache cache = new PathChildrenCache(client, path, true);
		cache.start(StartMode.POST_INITIALIZED_EVENT);
		cache.getListenable().addListener(new PathChildrenCacheListener() {
			public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
				if (event.getType().equals(PathChildrenCacheEvent.Type.INITIALIZED)) {

				} else if (event.getType().equals(PathChildrenCacheEvent.Type.CHILD_ADDED)) {

				} else if (event.getType().equals(PathChildrenCacheEvent.Type.CHILD_REMOVED)) {
					String path = event.getData().getPath();
					log.info("接受到watch监听事件，节点路径为：" + path);
					if(path.contains("XLock")) {
						log.info("分布式锁类型：排它锁。收到排它锁释放的事件通知...");						
						xLocklatch.countDown();
					}else if(path.contains("ShareLock")) {
						log.info("分布式锁类型：共享锁。收到排它锁释放的事件通知...");	
						// 收到自己的通知就不处理
						if(path.contains(selfIdentity)) {
							return;
						}
						List<String> lockChildrens = client.getChildren().forPath("/ShareLock");
						boolean isLock = false;
						try{
							if(selfIdentity.startsWith(READ_OPERATOR)) {
								isLock = canGetLock(lockChildrens, 0, selfIdentity.substring(0, selfIdentity.length() - 1), true);
							}
							else if(selfIdentity.startsWith(WRITE_OPERATOR)) {
								isLock = canGetLock(lockChildrens, 1, selfIdentity.substring(0, selfIdentity.length() - 1), true);
							}
						} catch(Exception e) {
							e.printStackTrace();
						}
						log.info("收到锁释放监听后，重新尝试获取锁，结果为:"+isLock);
						if(isLock){
							// 获得锁
							log.info("获得共享锁，解除由于获取不到锁的阻塞");
							shareLocklatch.countDown();
						}
					}
				} else if (event.getType().equals(
						PathChildrenCacheEvent.Type.CHILD_UPDATED)) {

				}
			}
		});
	}
}
