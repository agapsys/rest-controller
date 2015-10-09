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

import java.util.LinkedList;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.servlet.http.HttpServletRequest;

/**
 * Specialization of Action servlet to manage transactions.
 * A transaction will be initialized after each mapped action and will be committed (when action is successfully processed) or rollbacked (if there is an error while processing an acation).
 * @author Leandro Oliveira (leandro@agapsys.com)
 */
public abstract class JpaTransactionServlet extends ActionServlet {
	// CLASS SCOPE =============================================================
	/** Name of request attribute containing the transaction. */
	public static final String REQ_ATTR_TRANSACTION    = "com.agapsys.web.action.dispatcher.transaction";
	
	private static class ServletJpaTransaction extends WrappedEntityTransaction implements RequestTransaction {
		private final UnsupportedOperationException exception = new UnsupportedOperationException("Transaction is managed by servlet");
		private final EntityManager em;
		private final RequestResponsePair rrp;
		private final UserManager userManager;
		private final List<Runnable> commitQueue = new LinkedList<>();
		private final List<Runnable> rollbackQueue = new LinkedList<>();
		
		public ServletJpaTransaction(UserManager userManager, RequestResponsePair rrp, ServletJpaEntityManger em, EntityTransaction wrappedTransaction) {
			super(wrappedTransaction);
			this.userManager = userManager;
			this.rrp = rrp;
			this.em = em;			
		}
		
		private void processQueue(List<Runnable> queue) {
			for (Runnable runnable : queue) {
				runnable.run();
			}
			queue.clear();
		}
		
		@Override
		public void commit() {
			throw exception;
		}
		public void wrappedCommit() {
			super.commit();
			processQueue(commitQueue);
		}
		
		@Override
		public void begin() {
			throw exception;
		}
		public void wrappedBegin() {
			super.begin();
		}

		@Override
		public void rollback() {
			throw exception;
		}
		public void wrappedRollback() {
			super.rollback();
			processQueue(rollbackQueue);
		}

		@Override
		public EntityManager getEntityManager() {
			return em;
		}

		@Override
		public ApplicationUser getSessionUser() {
			return userManager.getSessionUser(rrp);
		}

		private void invokeAfter(List<Runnable> queue, Runnable runnable) {
			if (runnable == null)
				throw new IllegalArgumentException("Null runnable");
			
			queue.add(runnable);
		}
		
		@Override
		public void invokeAfterCommit(Runnable runnable) {
			invokeAfter(commitQueue, runnable);
		}

		@Override
		public void invokeAfterRollback(Runnable runnable) {
			invokeAfter(rollbackQueue, runnable);
		}
	}
	
	private static class ServletJpaEntityManger extends WrappedEntityManager {
		private final UnsupportedOperationException exception = new UnsupportedOperationException("Entity manager is managed by servlet");
		private final ServletJpaTransaction singleTransaction;
		
		public ServletJpaEntityManger(UserManager userManager, RequestResponsePair rrp, EntityManager wrappedEntityManager) {
			super(wrappedEntityManager);
			singleTransaction = new ServletJpaTransaction(userManager, rrp, this, super.getTransaction());
		}

		@Override
		public EntityTransaction getTransaction() {
			return singleTransaction;
		}

		@Override
		public void close() {
			throw exception;
		}
		public void wrappedClose() {
			super.close();
		}
	}
	// =========================================================================

	// INSTANCE SCOPE ==========================================================
	private void closeTransaction(Throwable t, RequestResponsePair rrp) {
		HttpServletRequest req = rrp.getRequest();
		ServletJpaTransaction transaction = (ServletJpaTransaction) req.getAttribute(REQ_ATTR_TRANSACTION);
		
		if (transaction != null) {
			if (t != null) {
				transaction.wrappedRollback();
			} else {
				transaction.wrappedCommit();
			}
			
			((ServletJpaEntityManger)transaction.getEntityManager()).wrappedClose();
			req.removeAttribute(REQ_ATTR_TRANSACTION);
		}
	}

	@Override
	protected void onError(Throwable throwable, RequestResponsePair rrp) {
		closeTransaction(throwable, rrp);
		super.onError(throwable, rrp);
	}
	
	@Override
	protected void afterAction(RequestResponsePair rrp) {
		closeTransaction(null, rrp);
		super.afterAction(rrp);
	}
	
	/**
	 * Returns the transaction associated with given request.
	 * Multiple calls to this methods passing the same request will return the same transaction instance.
	 * @param req HTTP request
	 * @return the transaction associated with given request
	 */
	public RequestTransaction getTransaction(RequestResponsePair rrp) {
		HttpServletRequest req = rrp.getRequest();
		ServletJpaTransaction transaction = (ServletJpaTransaction) req.getAttribute(REQ_ATTR_TRANSACTION);
		
		if (transaction == null) {
			transaction = (ServletJpaTransaction) new ServletJpaEntityManger(getUserManager(), rrp, getEntityManagerFactory().getEntityManager()).getTransaction();
			transaction.wrappedBegin();
			req.setAttribute(REQ_ATTR_TRANSACTION, transaction);
		}
		
		return transaction;
	}
	
	/** 
	 * Return the factory of entity managers used by this servlet. 
	 * <b>ATTENTION:</b>This method may be called multiple times during runtime. Do not create a new instance after each call in order to improve performance.
	 * @return {@link EntityManagerFactory} instance used by this servlet
	 */
	protected abstract EntityManagerFactory getEntityManagerFactory();
	// =========================================================================
}