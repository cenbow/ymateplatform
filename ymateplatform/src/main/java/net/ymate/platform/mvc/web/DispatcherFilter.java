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
package net.ymate.platform.mvc.web;

import net.ymate.platform.mvc.web.context.IWebRequestContext;
import net.ymate.platform.mvc.web.support.DispatchHelper;
import org.apache.commons.lang.StringUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * <p>
 * DispatcherFilter
 * </p>
 * <p>
 * WebMVC请求分发调度过滤器；
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
 *          <td>2012-12-23下午11:19:39</td>
 *          </tr>
 *          </table>
 */
public class DispatcherFilter implements Filter {

	private static final String IGNORE = "^.+\\.(jsp|jspx|png|gif|jpg|jpeg|js|css|swf|ico|htm|html|eot|woff|woff2|ttf|svg)$";

	private Pattern ignorePatern;

	private FilterConfig __filterConfig;

	private DispatchHelper __dispHelper;

	/* (non-Javadoc)
	 * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
	 */
	public void init(FilterConfig filterConfig) throws ServletException {
		__filterConfig = filterConfig;
		String _regx = StringUtils.defaultIfEmpty(__filterConfig.getInitParameter("ignore"), IGNORE);
        if (!"false".equalsIgnoreCase(_regx)) {
        	ignorePatern = Pattern.compile(_regx, Pattern.CASE_INSENSITIVE);
        }
        __dispHelper = new DispatchHelper(filterConfig);
	}

	/* (non-Javadoc)
	 * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
	 */
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		// 设置默认编码和内容类型
		request.setCharacterEncoding(WebMVC.getConfig().getCharsetEncoding());
		response.setCharacterEncoding(WebMVC.getConfig().getCharsetEncoding());
		//
		IWebRequestContext _context = __dispHelper.bindRequestContext((HttpServletRequest) request);
		if (null == ignorePatern || !ignorePatern.matcher(_context.getUrl()).find()) {
			response.setContentType("text/html;charset=" + WebMVC.getConfig().getCharsetEncoding());
			__dispHelper.doRequestProcess(_context, __filterConfig.getServletContext(), (HttpServletRequest) request, (HttpServletResponse) response);
        } else {
        	chain.doFilter(request, response);
        }
	}

	/* (non-Javadoc)
	 * @see javax.servlet.Filter#destroy()
	 */
	public void destroy() {
	}

}
