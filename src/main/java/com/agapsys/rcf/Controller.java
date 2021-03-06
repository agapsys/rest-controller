/*
 * Copyright 2015 Agapsys Tecnologia Ltda-ME.
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
package com.agapsys.rcf;

import com.agapsys.rcf.exceptions.ClientException;
import com.agapsys.rcf.exceptions.ForbiddenException;
import com.agapsys.rcf.exceptions.UnauthorizedException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Servlet responsible by mapping methods to actions
 */
public class Controller extends ActionServlet {

    // <editor-fold desc="STATIC SCOPE">
    // ========================================================================
    public static final String METHOD_NAME_MAPPING = "?";

    // <editor-fold desc="Private static members" defaultstate="collapsed">
    // -------------------------------------------------------------------------
    private static final Set<String> EMPTY_ROLE_SET = Collections.unmodifiableSet(new LinkedHashSet<String>());
    private static final Object[]    EMPTY_OBJ_ARRAY = new Object[] {};
    // -------------------------------------------------------------------------
    // </editor-fold>

    /** Defines a Data Transfer Object */
    public static interface Dto<T> {

        /**
         * Returns a transfer object associated with this instance.
         *
         * @return a transfer object associated with this instance.
         */
        public T getDto();
    }

    /** Name of the session attribute used to store current user. */
    public static final String SESSION_ATTR_USER = Controller.class.getName() + ".SESSION_ATTR_USER";

    /** Name of the default session attribute used to store XSRF token. */
    public static final String SESSION_ATTR_XSRF_TOKEN = Controller.class.getName() + ".SESSION_ATTR_XSRF_TOKEN";

    /** Name of the cookie used to send/retrieve a XSRF token. */
    public static final String XSRF_COOKIE = "XSRF-TOKEN";

    /** Name of the header used to send/retrieve a XSRF token. */
    public static final String XSRF_HEADER  = "X-XSRF-TOKEN";

    // Default size of XSRF token
    private static final int XSRF_TOKEN_LENGTH = 128;

    private static String __getRandom(int length) {
        char[] chars = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
        return __getRandom(length, chars);
    }

    private static String __getRandom(int length, char[] chars)  {
        if (length < 1) throw new IllegalArgumentException("Invalid length: " + length);

        if (chars == null || chars.length == 0) throw new IllegalArgumentException("Null/Empty chars");

        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            char c = chars[random.nextInt(chars.length)];
            sb.append(c);
        }

        return sb.toString();
    }
    // =========================================================================
    // </editor-fold>

    private class MethodCallerAction implements Action {

        private final String[] requiredRoles;
        private final long     requiredPerms;
        private final Method   method;
        private final boolean  secured;

        private MethodCallerAction(Method method, boolean secured, String[] requiredRoles, long requiredPerms) {
            if (!Modifier.isPublic(method.getModifiers()))
                throw new RuntimeException("Action method is not public: " + method.toGenericString());

            this.method = method;
            this.requiredRoles = requiredRoles;
            this.requiredPerms = requiredPerms;
            this.secured = secured || requiredRoles.length > 0 || requiredPerms != 0;
        }

        private Object[] __getCallParams(Method method, ActionRequest request, ActionResponse response) throws IOException {
            if (method.getParameterCount() == 0) return EMPTY_OBJ_ARRAY;

            List argList = new LinkedList();

            for (Parameter param : method.getParameters()) {
                Class<?> paramClass = param.getType();
                
                //<editor-fold defaultstate="collapsed" desc="It's an ActionRequest">
                if (ActionRequest.class.isAssignableFrom(paramClass)) {
                    if (paramClass == ActionRequest.class) {
                        argList.add(request);
                    } else {
                        try {
                            Constructor constructor = paramClass.getConstructor(ActionRequest.class);
                            Object customRequest = constructor.newInstance(request);
                            argList.add(customRequest);
                        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                            throw new RuntimeException(String.format("Cannot create request instance for %s", paramClass.getName()));
                        }
                    }
                    
                    continue;
                }
                //</editor-fold>
                
                //<editor-fold defaultstate="collapsed" desc="It's an ActionResponse">
                if (ActionResponse.class.isAssignableFrom(paramClass)) {
                    if (paramClass == ActionResponse.class) {
                        argList.add(response);
                    } else {
                        try {
                            Constructor constructor = paramClass.getConstructor(ActionResponse.class);
                            Object customResponse = constructor.newInstance(response);
                            argList.add(customResponse);
                        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                            throw new RuntimeException(String.format("Cannot create response instance for %s", paramClass.getName()));
                        }
                    }
                    
                    continue;
                }
                //</editor-fold>
                
                //<editor-fold defaultstate="collapsed" desc="It's an HttpServletRequest">
                if (HttpServletRequest.class.isAssignableFrom(paramClass)) {
                    argList.add(request.getServletRequest());
                    continue;
                }
                //</editor-fold>

                //<editor-fold defaultstate="collapsed" desc="It's an HttpServletResponse">
                if (HttpServletResponse.class.isAssignableFrom(paramClass)) {
                    argList.add(response.getServletResponse());
                    continue;
                }
                //</editor-fold>

                //<editor-fold defaultstate="collapsed" desc="It's a json for an object or a list of objects">
                JsonRequest jsonRequest = new JsonRequest(request);

                if (Collection.class.isAssignableFrom(paramClass)) {
                    // Must be a list...
                    if (!List.class.isAssignableFrom(paramClass))
                        throw new UnsupportedOperationException(String.format("Unsupported param type: %s", paramClass));
                    
                    Type pType = param.getParameterizedType();
                    if (! (pType instanceof ParameterizedType))
                        throw new UnsupportedOperationException("Missing list element type");
                    
                    Type elementType = ((ParameterizedType) pType).getActualTypeArguments()[0];
                    if (!elementType.getClass().equals(Class.class))
                        throw new UnsupportedOperationException("Unsupported list element type: " + elementType);
                    
                    argList.add(jsonRequest.readList((Class)elementType));
                } else {
                    // It's an object...
                    argList.add(jsonRequest.readObject(paramClass));
                }
                //</editor-fold>
            }

            return argList.toArray();
        }

        private void __checkSecurity(ActionRequest request, ActionResponse response) throws ServletException, IOException, UnauthorizedException, ForbiddenException {
            if (secured) {
                User user = getUser(request);

                if (user == null)
                    throw new UnauthorizedException("Unauthorized");

                Set<String> userRoles = user.getRoles();

                if (userRoles == null)
                    userRoles = EMPTY_ROLE_SET;

                for (String requiredRole : requiredRoles) {
                    if (!userRoles.contains(requiredRole))
                        throw new ForbiddenException();
                }

                long userPerms = user.getPermissions();

                if ((userPerms & requiredPerms) != requiredPerms)
                    throw new ForbiddenException();
            }
        }

        private Object __getSingleDto(Object obj) {
            if (obj == null)
                return null;

            if (obj instanceof Dto)
                return ((Dto) obj).getDto();

            return obj;
        }

        private List __getDtoList(List objList) {
            List dto = new LinkedList();

            for (Object obj : objList) {
                dto.add(__getSingleDto(obj));
            }

            return dto;
        }

        private Map __getDtoMap(Map<Object, Object> objMap) {
            Map dto = new LinkedHashMap();

            for (Map.Entry entry : objMap.entrySet()) {
                dto.put(__getSingleDto(entry.getKey()), __getSingleDto(entry.getValue()));
            }

            return dto;
        }

        private Set __getDtoSet(Set objSet) {
            Set dto = new LinkedHashSet();

            for (Object obj : objSet) {
                dto.add(__getSingleDto(obj));
            }

            return dto;
        }

        private Object __getDtoObject(Object src) {

            Object dto;

            if (src instanceof List) {
                dto = __getDtoList((List) src);
            } else if (src instanceof Set) {
                dto = __getDtoSet((Set) src);
            } else if (src instanceof Map) {
                dto = __getDtoMap((Map<Object, Object>) src);
            } else {
                dto = __getSingleDto(src);
            }

            return dto;
        }

        @Override
        public void processRequest(ActionRequest request, ActionResponse response) throws ServletException, IOException {
            try {
                __checkSecurity(request, response);

                Object[] callParams = __getCallParams(method, request, response);

                Object returnedObj = method.invoke(Controller.this, callParams);

                if (returnedObj == null && method.getReturnType().equals(Void.TYPE))
                    return;

                sendObject(request, response, __getDtoObject(returnedObj));

            } catch (InvocationTargetException | IllegalAccessException ex) {
                if (ex instanceof InvocationTargetException) {
                    Throwable targetException = ((InvocationTargetException) ex).getTargetException();

                    if (targetException instanceof ClientException) {
                        throw (ClientException) targetException;
                    } else {
                        throw new RuntimeException(targetException);
                    }
                }

                throw new RuntimeException(ex);
            }
        }

    }

    @Override
    protected final void onInit() {
        super.onInit();

        Class<? extends Controller> actionServletClass = Controller.this.getClass();

        // Check for WebAction annotations...
        Method[] methods = actionServletClass.getDeclaredMethods();

        for (Method method : methods) {
            WebActions webActionsAnnotation = method.getAnnotation(WebActions.class);
            WebAction[] webActions;

            if (webActionsAnnotation == null) {
                WebAction webAction = method.getAnnotation(WebAction.class);
                if (webAction == null) {
                    webActions = new WebAction[]{};
                } else {
                    webActions = new WebAction[]{webAction};
                }
            } else {
                webActions = webActionsAnnotation.value();
            }

            for (WebAction webAction : webActions) {
                HttpMethod[] httpMethods = webAction.httpMethods();
                String path = webAction.mapping().trim();

                if (path.equals(METHOD_NAME_MAPPING)) {
                    path = "/" + method.getName();
                }

                MethodCallerAction action = new MethodCallerAction(method, webAction.secured(), webAction.requiredRoles(), webAction.requiredPerms());

                for (HttpMethod httpMethod : httpMethods) {
                    registerAction(httpMethod, path, action);
                }
            }
        }

        onControllerInit();
    }

    /**
     * Called during controller initialization. Default implementation does nothing.
     */
    protected void onControllerInit() {}

    /**
     * This method instructs the controller how to retrieve the user associated with given HTTP exchange.
     *
     * @param request HTTP request. Default implementation checks for header {@linkplain Controller#XSRF_HEADER} in order to prevent XRSF attacks.
     * @return an user associated with given request. Default uses servlet request session to retrive the user. If a user cannot be retrieved from given request, returns null.
     * @throws ServletException if the HTTP request cannot be handled.
     * @throws IOException if an input or output error occurs while the servlet is handling the HTTP request.
     */
    protected User getUser(ActionRequest request) throws ServletException, IOException {
        HttpSession session = request.getServletRequest().getSession(false);

        if (session == null)
            return null;

        User user = (User) session.getAttribute(SESSION_ATTR_USER);

        if (user == null)
            return null;

        String sessionToken = (String) session.getAttribute(SESSION_ATTR_XSRF_TOKEN);
        String requestToken = request.getHeader(XSRF_HEADER);

        if (!Objects.equals(sessionToken, requestToken))
            return null;

        return user;
    }

    /**
     * This method instructs the controller how to associate an user with a HTTP exchange.
     *
     * Default implementation uses servlet request session associated with given request.
     * Default implementation also sends a cookie ({@linkplain Controller#XSRF_COOKIE}) containing a XRSF token which must be sent on each request associated with given user
     * as a header ({@linkplain Controller#XSRF_HEADER}).
     *
     * @param request HTTP request.
     * @param response HTTP response.
     * @param user user to be registered with given HTTP exchange. Passing null unregisters the user associated with given request.
     * @throws ServletException if the HTTP request cannot be handled.
     * @throws IOException if an input or output error occurs while the servlet is handling the HTTP request.
     */
    protected void setUser(ActionRequest request, ActionResponse response, User user) throws ServletException, IOException {

        if (user == null) {
            HttpSession session = request.getServletRequest().getSession(false);

            if (session != null) {
                session.removeAttribute(SESSION_ATTR_USER);
                session.removeAttribute(SESSION_ATTR_XSRF_TOKEN);
                response.removeCookie(XSRF_COOKIE);
            }

        } else {
            HttpSession session = request.getServletRequest().getSession();
            session.setAttribute(SESSION_ATTR_USER, user);

            String xsrfToken = __getRandom(XSRF_TOKEN_LENGTH);
            session.setAttribute(SESSION_ATTR_XSRF_TOKEN, xsrfToken);

            response.addCookie(XSRF_COOKIE, xsrfToken, -1);
        }

    }

    /**
     * This method instructs the controller how to send an object to the client.
     *
     * Default implementation serializes the DTO into a JSON response.
     *
     * @param request HTTP request.
     * @param response HTTP response.
     * @param obj object to be sent to the client.
     * @throws ServletException if the HTTP request cannot be handled.
     * @throws IOException if an input or output error occurs while the servlet is handling the HTTP request.
     */
    protected void sendObject(ActionRequest request, ActionResponse response, Object obj) throws ServletException, IOException {
        new JsonResponse(response).sendObject(obj);
    }

    @Override
    protected boolean onUncaughtError(ActionRequest request, ActionResponse response, RuntimeException uncaughtError) throws ServletException, IOException {
        Throwable cause = uncaughtError.getCause(); // <-- MethodCallerAction throws the target exception wrapped in a RuntimeException

        if (cause == null)
            cause = uncaughtError;

        if (cause instanceof ServletException)
            throw (ServletException) cause;

        if (cause instanceof IOException)
            throw (IOException) cause;

        return super.onUncaughtError(request, response, uncaughtError);
    }

}
