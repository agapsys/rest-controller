/*
 * Copyright 2016 Agapsys Tecnologia Ltda-ME.
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

/**
 * @author Leandro Oliveira (leandro@agapsys.com)
 */
class RuntimeException extends java.lang.RuntimeException{
	
	public RuntimeException(String msg, Object...msgArgs) {
		super(msgArgs.length > 0 ? String.format(msg, msgArgs) : msg);
	}

	public RuntimeException(Throwable cause) {
		super(cause);
	}

	public RuntimeException(Throwable cause, String msg, Object...msgArgs) {
		super(msgArgs.length > 0 ? String.format(msg, msgArgs) : msg, cause);
	}
	
}