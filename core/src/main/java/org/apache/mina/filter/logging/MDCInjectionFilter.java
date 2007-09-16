package org.apache.mina.filter.logging;

import org.apache.mina.common.*;
import org.apache.mina.filter.util.WrappingFilter;
import org.slf4j.MDC;

import java.net.InetSocketAddress;
import java.util.*;

/**
 * This filter will inject some key IoSession properties into the Mapped Diagnostic Context (MDC)
 * <p/>
 * These properties will be set in the MDC for all logging events that are generated
 * down the call stack, even in code that is not aware of MINA.
 *
 * The following properties will be set for all transports:
 * <ul>
 *  <li>"IoHandlerClass"</li>
 *  <li>"remoteAddress"</li>
 *  <li>"localAddress"</li>
 * </ul>
 *
 * When <code>session.getTransportMetadata().getAddressType() == InetSocketAddress.class</code>
 * the following properties will also be set:
 * <ul>
 * <li>"remoteIp"</li>
 * <li>"remotePort"</li>
 * <li>"localIp"</li>
 * <li>"localPort"</li>
 * </ul>
 *
 * User code can also add properties to the context, via
 *
 * If you only want the MDC to be set for the IoHandler code, it's enough to add
 * one MDCInjectionFilter at the end of the filter chain.
 *
 * If you want the MDC to be set for ALL code, you should
 *   add an MDCInjectionFilter to the start of the chain
 *   and add one after EVERY ExecutorFilter in the chain
 *
 * @author The Apache MINA Project (dev@mina.apache.org)
 * @version $Rev: 566952 $, $Date: 2007-08-17 09:25:04 +0200 (vr, 17 aug 2007) $
 */

public class MDCInjectionFilter extends WrappingFilter {

    /** key used for storing the context map in the IoSession */
    private static final String CONTEXT_KEY = MDCInjectionFilter.class + ".CONTEXT_KEY";

    private ThreadLocal<Integer> callDepth = new ThreadLocal<Integer>() {
        protected Integer initialValue() {
            return 0;
        }
    };

    @Override
    protected void wrap(IoEventType eventType, IoSession session, Runnable action) {
        // since this method can potentially call into itself
        // we need to check the call depth before clearing the MDC
        callDepth.set (callDepth.get() + 1);
        Context context = getContext(session);
        /* copy context to the MDC */
        for (Map.Entry<String,String> e : context.entrySet()) {
            MDC.put(e.getKey(), e.getValue());
        }
        try {
            /* propagate event down the filter chain */
            action.run();
        } finally {
            callDepth.set ( callDepth.get() - 1);
            if (callDepth.get() == 0) {
                /* remove context from the MDC */
                for (Object key : context.keySet()) {
                    MDC.remove(key.toString());
                }
                MDC.remove("name");
            }
        }
    }


    public static Context getContext(final IoSession session) {
        Context context = (Context) session.getAttribute(CONTEXT_KEY);
        if (context == null) {
            context = new Context();
            fillContext(session, context);
            session.setAttribute(CONTEXT_KEY, context);
        }
        return context;
    }

    /**
     * write key properties of the session to the Mapped Diagnostic Context
     * sub-classes could override this method to map more/other attributes
     * @param session the session to map
     * @param context key properties will be added to this map
     */
    protected static void fillContext(final IoSession session, final Context context) {
        context.put("IoHandlerClass", session.getHandler().getClass().getName());
        context.put("remoteAddress", session.getRemoteAddress().toString());
        context.put("localAddress", session.getLocalAddress().toString());
        if (session.getTransportMetadata().getAddressType() == InetSocketAddress.class) {
            InetSocketAddress remoteAddress = (InetSocketAddress) session.getRemoteAddress();
            InetSocketAddress localAddress  = (InetSocketAddress) session.getLocalAddress();
            context.put("remoteIp", remoteAddress.getAddress().getHostAddress());
            context.put("remotePort", String.valueOf(remoteAddress.getPort()));
            context.put("localIp", localAddress.getAddress().getHostAddress());
            context.put("localPort", String.valueOf(localAddress.getPort()));
        }
    }

    /**
     * Add a property to the context for the given session
     * This property will be added to the MDC for all subsequent events
     * @param session The session for which you want to set a property
     * @param key  The name of the property
     * @param value The value of the property
     */
    public static void setProperty (IoSession session, String key, String value) {
        Context context = getContext(session);  
        context.put(key, value);        
    }

    private static class Context extends HashMap<String,String> {
    }
    
}
