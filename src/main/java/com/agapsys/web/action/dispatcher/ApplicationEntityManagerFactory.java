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

import javax.persistence.EntityManager;

/**
 * This is the factory of {@linkplain EntityManager} instances used by application
 * @author Leandro Oliveira (leandro@agapsys.com)
 */
public interface ApplicationEntityManagerFactory {
	/**
	 * Returns an {@linkplain EntityManager} instance to be used by application.
	 * @return {@linkplain EntityManager} instance.
	 */
	public EntityManager getEntityManager();
}
