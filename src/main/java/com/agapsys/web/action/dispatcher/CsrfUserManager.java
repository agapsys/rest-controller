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
 * User manager which handles CSRF security
 * @author Leandro Oliveira (leandro@agapsys.com)
 */
public class CsrfUserManager extends UserManager {
	// CLASS SCOPE =============================================================
	private static final CsrfSecurityHandler DEFAULT_CSRF_SECURITY_HANDLER = new CsrfSecurityHandler();
	// =========================================================================

	// INSTANCE SCOPE ==========================================================
	@Override
	public void setSessionUser(ApplicationUser user, RequestResponsePair rrp) {
		super.setSessionUser(user, rrp);
		String csrfToken = DEFAULT_CSRF_SECURITY_HANDLER.generateCsrfToken();
		DEFAULT_CSRF_SECURITY_HANDLER.setSessionCsrfToken(csrfToken, rrp);
		DEFAULT_CSRF_SECURITY_HANDLER.sendCsrfToken(csrfToken, rrp);
	}

	@Override
	public void clearSessionUser(RequestResponsePair rrp) {
		super.clearSessionUser(rrp);
		DEFAULT_CSRF_SECURITY_HANDLER.clearCsrfToken(rrp);
	}
	
	/**
	 * Returns the CSRF security handler used by this instance.
	 * @return the CSRF security handler used by this instance.
	 */
	public CsrfSecurityHandler getCsrfSecurityHandler() {
		return DEFAULT_CSRF_SECURITY_HANDLER;
	}
	// =========================================================================
}
