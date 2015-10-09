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

package com.agapsys.web.action.dispatcher;

/**
 * User manager.
 * A user manager is the object responsible by handling user in a request session
 * @author Leandro Oliveira (leandro@agapsys.com)
 */
public class UserManager {
	// CLASS SCOPE =============================================================
	private static final String SESSION_ATTR_USER = "com.agapsys.web.user";
	// =========================================================================

	// INSTANCE SCOPE ==========================================================
	/**
	 * Returns a user from session
	 * @param rrp request-response pair
	 * @return session user or null if there is no user
	 */
	public ApplicationUser getSessionUser(RequestResponsePair rrp) {
		return (ApplicationUser) rrp.getRequest().getSession().getAttribute(SESSION_ATTR_USER);
	}
	
	/**
	 * Sets a user in a session
	 * @param rrp request-response pair
	 * @param user user to be registered.
	 */
	public void setSessionUser(ApplicationUser user, RequestResponsePair rrp) {
		if (user == null)
			throw new IllegalArgumentException("Null user");
		
		rrp.getRequest().getSession().setAttribute(SESSION_ATTR_USER, user);
	}
	
	/**
	 * Clears session user
	 * @param rrp request-response pair
	 */
	public void clearSessionUser(RequestResponsePair rrp) {
		rrp.getRequest().getSession().removeAttribute(SESSION_ATTR_USER);
	}
	// =========================================================================
}
