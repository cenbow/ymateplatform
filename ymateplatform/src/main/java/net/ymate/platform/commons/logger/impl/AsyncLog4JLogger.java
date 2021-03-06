/*
 * Copyright 2007-2107 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ymate.platform.commons.logger.impl;

import java.io.File;
import java.util.Enumeration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.ymate.platform.base.YMP;
import net.ymate.platform.commons.i18n.I18N;
import net.ymate.platform.commons.lang.PairObject;
import net.ymate.platform.commons.logger.AbstractLogger;
import net.ymate.platform.commons.logger.ILogger;
import net.ymate.platform.commons.logger.Logs;
import net.ymate.platform.commons.util.DateTimeUtils;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;


/**
 * <p>
 * AsyncLog4JLogger
 * </p>
 * <p>
 * 异常线程日志记录器（基于Log4J实现）；
 * </p>
 * 
 * @author 刘镇(suninformation@163.com)
 * @version 0.0.0
 *          <table style="border:1px solid gray;">
 *          <tr>
 *          <th width="100px">版本号</th><th width="100px">动作</th><th
 *          width="100px">修改人</th><th width="100px">修改时间</th>
 *          </tr>
 *          <!-- 以 Table 方式书写修改历史 -->
 *          <tr>
 *          <td>0.0.0</td>
 *          <td>创建类</td>
 *          <td>刘镇</td>
 *          <td>2010-8-2上午10:09:29</td>
 *          </tr>
 *          </table>
 */
public class AsyncLog4JLogger extends AbstractLogger implements ILogger{

	/** 由 Apache Log4J 实现的日志输出 */
	private Logger logger;

	/** 日志队列，Map<level, Map<message, e>>，级别level不可能为空 */
	private LinkedBlockingQueue<PairObject<Integer, PairObject<Object, Throwable>>> logQueue;

	/** 日志输出线程 */
	private Thread logThread;

	private boolean isStoped = true;

	/**
	 * 构造器
	 */
	public AsyncLog4JLogger() {
		logQueue = new LinkedBlockingQueue<PairObject<Integer, PairObject<Object, Throwable>>>();
	}

	public boolean has(String loggerName) {
		if (__IS_INITED) {
			Logger l = Logger.getLogger(loggerName);
			if (l != null) {
				Enumeration<?> e = l.getAllAppenders();
				if (e != null && e.hasMoreElements()) {
					return true;
				}
			}
		}
		return false;
	}

	public void trace(String info, Throwable e) {
		bindExpInfo(info, e, LogLevel.TRACE);
	}

	public void trace(String info) {
		bindExpInfo(info, null, LogLevel.TRACE);
	}

	public void debug(String info, Throwable e) {
		bindExpInfo(info, e, LogLevel.DEBUG);
	}

	public void debug(String info) {
		bindExpInfo(info, null, LogLevel.DEBUG);
	}

	public void fatal(String info, Throwable e) {
		bindExpInfo(info, e, LogLevel.FATAL);
	}

	public void fatal(String info) {
		bindExpInfo(info, null, LogLevel.FATAL);
	}

	public void error(String info, Throwable e) {
		bindExpInfo(info, e, LogLevel.ERROR);
	}

	public void error(String info) {
		bindExpInfo(info, null, LogLevel.ERROR);
	}

	public void info(String info, Throwable e) {
		bindExpInfo(info, e, LogLevel.INFO);
	}
	
	public void info(String info) {
		bindExpInfo(info, null, LogLevel.INFO);
	}

	public void warn(String info, Throwable e) {
		bindExpInfo(info, e, LogLevel.WARN);
	}

	public void warn(String info) {
		bindExpInfo(info, null, LogLevel.WARN);
	}

	public void log(String info, Throwable e, LogLevel level) {
		bindExpInfo(info, e, null);
	}

	public void log(String info, LogLevel level) {
		bindExpInfo(info, null, null);
	}

	private void bindExpInfo(String info, Throwable e, LogLevel logLevel) {
		if (logLevel == null) {
			logLevel = LogLevel.ALL;
		}
		if (level.getValue() > logLevel.getValue()) {
			return;
		}
		StringBuilder sb = new StringBuilder(DateTimeUtils.formatTime(DateTimeUtils.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss.SSS"));
		sb.append(logLevel.getDisplayName());
		if (enableCallerInfo) {
			sb.append('[').append(Thread.currentThread().getId()).append(':').append(makeCallerInfo(callerDeepth)).append(']');
		}
		sb.append(' ').append(info);
		logQueue.add(new PairObject<Integer, PairObject<Object, Throwable>>(logLevel.getValue(), new PairObject<Object, Throwable>(sb, e)));
	}

	public void initialize(LogLevel defaultLevel, String loggerName) {
		if (!__IS_INITED) {
			File cfgFile = new File(Logs.getConfig().getLogCfgFile());
			if (!cfgFile.exists()) {
				System.err.println(I18N.formatMessage(YMP.__LSTRING_FILE, null, null, "ymp.commons.logger_config_file_not_found", Logs.getConfig().getLogCfgFile()));
				return;
			}
			System.out.println(I18N.formatMessage(YMP.__LSTRING_FILE, null, null, "ymp.commons.logger_config_file_load", cfgFile.getPath()));
			// 设置Property属性，方使在log4j.xml文件中直接引用${LOGS_DIR}属性值
			System.setProperty("LOGS_DIR", Logs.getConfig().getLogOutputDir());
			DOMConfigurator.configure(cfgFile.getPath());
			__IS_INITED = true;
		}
		level = defaultLevel;
		this.loggerName = loggerName;
		isPrintConsole = Logs.getConfig().allowPrintConsole();
		System.out.println(I18N.formatMessage(YMP.__LSTRING_FILE, null, null, isPrintConsole ? "ymp.commons.logger_init_with_console" : "ymp.commons.logger_init_without_console", loggerName));
		logger = Logger.getLogger(loggerName);
		logThread = new Thread("Logger[" + loggerName + "]Thread") {
			@Override
			public void run() {
				try {
					PairObject<Integer, PairObject<Object, Throwable>> logPObject;
					Object msg;
					while (!isStoped) {
						// 获取需要记录的日志内容，得到了就不为空
						logPObject = logQueue.poll(30, TimeUnit.SECONDS);
						if (logPObject == null) {
							continue;
						}
						msg = logPObject.getValue().getKey();
						if (logPObject.getValue().getValue() != null) {
							((StringBuilder) msg).append("- ").append(toStacksString(logPObject.getValue().getValue()));
						}
						if (logPObject.getKey() == LogLevel.ERROR.getValue()) {
							logger.error(msg);
						} else if (logPObject.getKey() == LogLevel.WARN.getValue()) {
							logger.warn(msg);
						} else if (logPObject.getKey() == LogLevel.INFO.getValue()) {
							logger.info(msg);
						} else if (logPObject.getKey() == LogLevel.DEBUG.getValue()) {
							logger.debug(msg);
						} else if (logPObject.getKey() == LogLevel.TRACE.getValue()) {
							logger.trace(msg);
						} else if (logPObject.getKey() == LogLevel.FATAL.getValue()) {
							logger.fatal(msg);
						} else {
							logger.debug(msg);
						}
						// 判断是否输出到控制台
						if (isPrintConsole) {
							System.out.println(msg);
						}
					}
				} catch (Exception e) {
					if (!isStoped) {
						System.err.println(I18N.formatMessage(YMP.__LSTRING_FILE, null, null, "ymp.commons.logger_stop_error", DateTimeUtils.formatTime(DateTimeUtils.currentTimeMillis(), null)));
						e.printStackTrace(System.err);
					}
				}
			}
		};
		logThread.setDaemon(true);
		isStoped = false;
		logThread.start();

		logger.info("\r\n");
		logger.info(I18N.formatMessage(YMP.__LSTRING_FILE, null, null, "ymp.commons.logger_startup", DateTimeUtils.formatTime(DateTimeUtils.currentTimeMillis(), null)));
	}

	public void destroy() {
		if (logThread != null) {
			isStoped = true;
			try {
				logThread.join();
			} catch (InterruptedException e) {
                // 忽略...
            }
		}
	}
}