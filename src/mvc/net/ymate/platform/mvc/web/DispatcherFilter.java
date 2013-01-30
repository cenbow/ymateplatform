/**
 * <p>文件名:	DispatcherFilter.java</p>
 * <p>版权:		详见产品版权说明书</p>
 * <p>公司:		YMateSoft Co., Ltd.</p>
 * <p>项目名：	yMatePlatform_V2_1</p>
 * <p>作者：		刘镇(suninformation@163.com)</p>
 */
package net.ymate.platform.mvc.web;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.ymate.platform.commons.logger.Logs;
import net.ymate.platform.mvc.support.RequestExecutor;
import net.ymate.platform.mvc.view.IView;
import net.ymate.platform.mvc.web.context.IWebRequestContext;
import net.ymate.platform.mvc.web.context.WebContext;
import net.ymate.platform.mvc.web.context.impl.WebRequestContext;
import net.ymate.platform.mvc.web.support.HttpMethodRequestWrapper;
import net.ymate.platform.mvc.web.view.impl.HttpStatusView;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.StopWatch;

/**
 * <p>
 * DispatcherFilter
 * </p>
 * <p>
 * WebMVC核心过滤器，控制器执行入口；
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

	private static final String IGNORE = "^.+\\.(jsp|png|gif|jpg|js|css|jspx|jpeg|swf|ico)$";
	
	private static final String DEFAULT_METHOD_PARAM = "_method";

	private Pattern ignorePatern;

	private String methodParam;

	private boolean __useRewrite;
	
	private String rewriteUrl;
	private List<String> rewriteIgnoreSuffixs;

	private String prefix;

	private FilterConfig __filterConfig;

	/* (non-Javadoc)
	 * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
	 */
	public void init(FilterConfig filterConfig) throws ServletException {
		__filterConfig = filterConfig;
		String _regx = StringUtils.defaultIfEmpty(__filterConfig.getInitParameter("ignore"), IGNORE);
        if (!"false".equalsIgnoreCase(_regx)) {
        	ignorePatern = Pattern.compile(_regx, Pattern.CASE_INSENSITIVE);
        }
        String _rewrite = StringUtils.defaultIfEmpty(__filterConfig.getInitParameter("rewrite"), "false");
        if (!"false".equalsIgnoreCase(_rewrite)) {
        	String[] _reParams = StringUtils.split(_rewrite, "^");
        	rewriteUrl = _reParams[0];
        	__useRewrite = true;
        	if (_reParams.length > 1) {
        		rewriteIgnoreSuffixs = Arrays.asList(StringUtils.split(_reParams[1], "|"));
        	}
        	if (rewriteIgnoreSuffixs == null || rewriteIgnoreSuffixs.isEmpty()) {
        		rewriteIgnoreSuffixs = Arrays.asList(StringUtils.split("jsp|jspx", "|"));
        	}
        }
        prefix = StringUtils.defaultIfEmpty(__filterConfig.getInitParameter("prefix"), "");
        methodParam = StringUtils.defaultIfEmpty(__filterConfig.getInitParameter("methodParam"), DEFAULT_METHOD_PARAM);
	}

	/* (non-Javadoc)
	 * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
	 */
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		// 设置默认编码
		request.setCharacterEncoding(WebMVC.getConfig().getCharsetEncoding());
		response.setContentType("text/html;charset=" + WebMVC.getConfig().getCharsetEncoding());
		// 尝试处理RESTFul请求Method包装
		HttpServletRequest _httpRequest = this.wrapperRequest((HttpServletRequest) request);
		IWebRequestContext _context = new WebRequestContext(_httpRequest, prefix);
		if (null == ignorePatern || !ignorePatern.matcher(_context.getUrl()).find()) {
			StopWatch _stopWatch = new StopWatch();
			_stopWatch.start();
			WebContext.setContext(new WebContext(WebContext.createWebContextMap(__filterConfig.getServletContext(), _httpRequest, (HttpServletResponse) response, null), _context));
			try {
				RequestExecutor _executor = WebMVC.processRequestMapping(_context);
				IView _view = (_executor != null) ? _executor.execute() : new HttpStatusView(HttpServletResponse.SC_NOT_FOUND);
				if (_view != null) {
					_view.render();
				} else {
					new HttpStatusView(HttpServletResponse.SC_NOT_FOUND).render();
				}
			} catch (Exception e) {
				IWebErrorHandler _errorHandler = WebMVC.getConfig().getErrorHandlerClassImpl();
				if (_errorHandler != null) {
					_errorHandler.onError(e);
				} else {
					throw new ServletException(e);
				}
			} finally {
				WebContext.setContext(null);
				_stopWatch.stop();
				Logs.info("控制器[" + _context.getRequestMapping() + "][" + _httpRequest.getMethod() + "]请求处理完毕，耗时" + _stopWatch.getTime() + "ms");
			}
			return;
        } else if (__useRewrite && !rewriteIgnoreSuffixs.contains(_context.getSuffix().toLowerCase()) && !StringUtils.equals(_httpRequest.getServletPath(), rewriteUrl)) {
        	request.getRequestDispatcher(rewriteUrl + _context.getUrl()).forward(request, response);
        } else {
        	chain.doFilter(request, response);
        }
	}

	protected HttpServletRequest wrapperRequest(HttpServletRequest request) {
		if (WebMVC.isInited() && WebMVC.getConfig().isRestfulModel()) {
			String paramValue = request.getParameter(this.methodParam);
			if ("POST".equals(request.getMethod()) && StringUtils.isNotBlank(paramValue)) {
				String method = paramValue.toUpperCase(Locale.ENGLISH);
				return new HttpMethodRequestWrapper(request, method);
			}
		}
		return request;
	}

	/* (non-Javadoc)
	 * @see javax.servlet.Filter#destroy()
	 */
	public void destroy() {
	}

}