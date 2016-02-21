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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration.Dynamic;

/**
 * Controller registration listener.
 * Reads META-INF/controllers.info file an registers all controllers with the application.
 * @author Leandro Oliveira (leandro@agapsys.com)
 */
public class ControllerRegistrationListener implements ServletContextListener {

	// STATIC SCOPE ============================================================	
	public static final String EMBEDDED_INFO_FILE = "META-INF/rcf.info";

	private static List<String> readEmbeddedInfo(String embeddedFileName, String encoding) {
		try (InputStream is = ControllerRegistrationListener.class.getClassLoader().getResourceAsStream(embeddedFileName)) {
			if (is != null)
				return readEmbeddedInfo(is, encoding);

			return new LinkedList<>();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	private static List<String> readEmbeddedInfo(InputStream is, String encoding) {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(is, encoding));
			List<String> lines = new LinkedList<>();
			String readLine;

			while ((readLine = in.readLine()) != null) {
				readLine = readLine.trim();

				if (readLine.isEmpty() || readLine.startsWith("#"))
					continue;

				lines.add(readLine);
			}

			return lines;
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	private static Map<String, Class<? extends Controller>> _getControllerMap() {
		List<String> lines = readEmbeddedInfo(EMBEDDED_INFO_FILE, "UTF-8");
		Map<String, Class<? extends Controller>> controllerMap = new LinkedHashMap<>();

		for (String line : lines) {
			String[] components = line.split(":");
			
			for (int i = 0 ; i < components.length; i++) {
				components[i] = components[i].trim();
			}
			
			String controllerClassName;
			String controllerMapping = null;
			
			Class<? extends Controller> controllerClass;
			
			if (components.length == 1) {
				controllerClassName = components[0];
			} else if (components.length == 2) {
				controllerMapping = components[0];
				controllerClassName = components[1];
			} else {
				throw new RuntimeException("Invalid embedded entry: " + line);
			}
			
			try {
				controllerClass = (Class<? extends Controller>) Class.forName(controllerClassName);
			} catch (ClassNotFoundException ex) {
				throw new RuntimeException(ex);
			}
			
			if (controllerMapping == null) {
				// Retrieve mapping via reflection
				WebController annotation = controllerClass.getAnnotation(WebController.class);
				if (annotation == null) {
					controllerMapping = controllerClass.getSimpleName();
				} else {
					controllerMapping = annotation.value();
					if (controllerMapping.trim().isEmpty())
						controllerMapping = controllerClass.getSimpleName();
				}
			}
			
			if (!controllerMapping.matches("^[a-zA-Z0-9]+[a-zA-Z\\-0-9\\/]*[^\\/\\*]+$"))
				throw new RuntimeException(String.format("Invalid controller mapping: %s => %s", controllerMapping, controllerClassName));

			if (controllerMap.put(controllerMapping, controllerClass) != null)
				throw new RuntimeException(String.format("Duplicate controller mapping: %s", controllerMapping));
		}

		return controllerMap;
	}
	// =========================================================================

	// INSTANCE SCOPE ==========================================================
	protected Map<String, Class<? extends Controller>> getControllerMap() {
		return _getControllerMap();
	}
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
        ServletContext sc = sce.getServletContext();
        if (sc.getMajorVersion() >= 3) {
			for (Map.Entry<String, Class<? extends Controller>> entry : getControllerMap().entrySet()) {
				Class<? extends Controller> controllerClass = entry.getValue();
				final Dynamic dn = sc.addServlet(controllerClass.getName(), controllerClass);
				
				String controllerName = entry.getKey();
				
				String urlPattern = String.format("/%s/*", controllerName);
				dn.addMapping(urlPattern);
			}
        } else {
			throw new RuntimeException("Web Controller Framework requires Servlet 3.x specification support");
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {}
	// =========================================================================
}