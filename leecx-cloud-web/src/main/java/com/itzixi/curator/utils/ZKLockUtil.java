package com.itzixi.curator.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCache.StartMode;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.log4j.Logger;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs.Ids;

/**
 * 
 * @Title: ZKLockUtil.java
 * @Package com.itzixi.curator.utils
 * @Description: 本类不能在同一台电脑上使用，由于static的作用，只能在不同的服务器上做
 * 				   不适用与伪分布式，需要在真实分布式环境下才会生效
 * Copyright: Copyright (c) 2017
 * Company:FURUIBOKE.SCIENCE.AND.TECHNOLOGY
 * 
 * @author leechenxiang
 * @date 2017年11月22日 下午1:15:44
 * @version V1.0
 */
public class ZKLockUtil {
	private static CuratorFramework client = null;
	private static Logger logger = Logger.getLogger(ZKLockUtil.class);
	
	// 用于排它锁
	protected static CountDownLatch xLocklatch = new CountDownLatch(1);
	
	// 用于共享锁
	protected static CountDownLatch shareLocklatch = new CountDownLatch(1);
	private static String selfIdentity=null;
	private static String selfNodeName= null;
	
	public static synchronized void init(String connectString) {
		// 创建zk客户端，如果存在则不需要再次创建
		if (client != null)
			return;

		// 创建zk客户端的同时创建一个命名空间"ZKLocks"，排它锁和共享锁都在这个命名空间下
		RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
		client = CuratorFrameworkFactory.builder().connectString(connectString)
				.sessionTimeoutMs(10000).retryPolicy(retryPolicy)
				.namespace("ZKLocks").build();
		// 启动客户端
		client.start();

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
			logger.error("zookeeper client connecting error... please try again...");
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
	public static synchronized void getXLock() {
		// 使用这个死循环，当且仅当获得锁成功后才会跳出循环
		while (true) {
			try {
				client.create()
						.creatingParentsIfNeeded()
						.withMode(CreateMode.EPHEMERAL)		// 分布式锁使用临时节点即可，因为会话失效需要释放锁
						.withACL(Ids.OPEN_ACL_UNSAFE)
						.forPath("/XLock/lock");
				logger.info("获得排它锁成功...");
				return;										// 如果节点创建成功，即说明获取锁成功
			} catch (Exception e) {
				logger.info("获得排它锁失败...");
				try {
					// 如果没有获取到锁，需要重新设置同步资源值
					if(xLocklatch.getCount() <= 0){
						xLocklatch = new CountDownLatch(1);
					}
					// 阻塞线程
					xLocklatch.await();
				} catch (InterruptedException e1) {
					e1.printStackTrace();
					logger.error("", e1);
				}
			}
		}
	}

	/**
	 * 
	 * @param type
	 *            0为读锁，1为写锁
	 * @param identity
	 *            获取当前锁的所有者
	 */
	public static boolean getShareLock(int type, String identity) {
		if (identity == null || "".equals(identity)) {
			throw new RuntimeException("identity不能为空");
		}
		if (identity.indexOf("-") != -1) {
			throw new RuntimeException("identity不能包含字符-");
		}
		if (type != 0 && type != 1) {
			throw new RuntimeException("type只能为0或者1");
		}
		String nodeName = null;
		if (type == 0) {
			nodeName = "R" + identity + "-";
		} else if (type == 1) {
			nodeName = "W" + identity + "-";
		}
		selfIdentity = nodeName;
		try {
			//if (client.checkExists().forPath("/ShareLock/" + nodeName) == null)
				 selfNodeName = client.create().creatingParentsIfNeeded()
						.withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
						.withACL(Ids.OPEN_ACL_UNSAFE)
						.forPath("/ShareLock/" + nodeName);
				logger.info("创建节点:"+selfNodeName);
			List<String> lockChildrens = client.getChildren().forPath(
					"/ShareLock");
			if (!canGetLock(lockChildrens, type,
					nodeName.substring(0, nodeName.length() - 1),false)) {
				shareLocklatch.await();
			}
			// return;// 获得锁成功就返回
		} catch (Exception e) {
			logger.info("出现异常", e);
			return false;
		}
		
		logger.info("成功获取锁");		
		return true;
	}

	private static boolean canGetLock(List<String> childrens, int type,
			String identity,boolean reps) {
		boolean res = false;
		if(childrens.size()<=0)
			return true;
		
		try {
			String currentSeq = null;
			List<String> seqs = new ArrayList<String>();
			//List<String> identitys = new ArrayList<String>();
			Map<String,String> seqs_identitys = new HashMap<String,String>();
			for (String child : childrens) {
				String splits[] = child.split("-");
				seqs.add(splits[1]);
				//identitys.add(splits[0]);
				seqs_identitys.put(splits[1], splits[0]);
				if (identity.equals(splits[0]))
					currentSeq = splits[1];
			}

			List<String> sortSeqs = new ArrayList<String>();
			sortSeqs.addAll(seqs);
			Collections.sort(sortSeqs);

			// 第一个节点，则无论是读锁还是写锁都可以获取
			if (currentSeq.equals(sortSeqs.get(0))) {
				res = true;
				logger.info("请求锁,因为是第一个请求锁的请求，所以获取成功");
				return res;
			} else {
				// 写锁
				if (type == 1) {
					res = false;
					//第一次请求取锁则设置监听，以后就不设置了，因为监听一直存在
					if(reps==false)
						addChildWatcher("/ShareLock");
					logger.info("请求写锁，因为前面有其它锁，所以获取锁失败");
					return res;
				}
			}
			// int index =-1;
			boolean hasW = true;
			for (String seq : sortSeqs) {
				// ++index;
				if (seq.equals(currentSeq)) {
					break;
				}
				if (!seqs_identitys.get(seq).startsWith("W"))
					hasW = false;
			}
			if (type == 0 && hasW == false) {
				res = true;
			} else if (type == 0 && hasW == true) {
				res = false;
			}
			if (res == false) {
				// 添加监听
				addChildWatcher("/ShareLock");
				logger.info("因为没有获取到锁，添加锁的监听器");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return res;
	}

	public static boolean releaseXLock() {
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
	
	public static boolean unlockForShareLock() {
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
	public static void addChildWatcher(String path) throws Exception {
		final PathChildrenCache cache = new PathChildrenCache(client, path, true);
		cache.start(StartMode.POST_INITIALIZED_EVENT);// ppt中需要讲StartMode
		// System.out.println(cache.getCurrentData().size());
		cache.getListenable().addListener(new PathChildrenCacheListener() {
			public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
				if (event.getType().equals(PathChildrenCacheEvent.Type.INITIALIZED)) {

				} else if (event.getType().equals(PathChildrenCacheEvent.Type.CHILD_ADDED)) {

				} else if (event.getType().equals(PathChildrenCacheEvent.Type.CHILD_REMOVED)) {
					String path = event.getData().getPath();
					logger.info("接受到watch监听事件，节点路径为：" + path);
					if(path.contains("XLock")){
						logger.info("分布式锁类型：排它锁。收到排它锁释放的事件通知...");						
						xLocklatch.countDown();
					}else if(path.contains("ShareLock")){
						logger.info("共享锁,收到锁释放通知");	
						//收到自己的通知就不处理
						if(path.contains(selfIdentity))
							return;
						List<String> lockChildrens = client.getChildren().forPath("/ShareLock");
						boolean isLock = false;
						try{
						if(selfIdentity.startsWith("R"))
							isLock = canGetLock(lockChildrens,0,selfIdentity.substring(0, selfIdentity.length() - 1),true);
						else if(selfIdentity.startsWith("W"))
							isLock = canGetLock(lockChildrens,1,selfIdentity.substring(0, selfIdentity.length() - 1),true);
						}catch(Exception e){
							e.printStackTrace();
						}
						logger.info("收到锁释放监听后，重新尝试获取锁，结果为:"+isLock);
						if(isLock){
							//获得锁
							logger.info("获得锁，解除因为获取不到锁的阻塞");
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
