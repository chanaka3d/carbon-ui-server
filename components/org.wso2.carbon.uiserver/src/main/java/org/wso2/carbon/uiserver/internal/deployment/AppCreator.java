/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.uiserver.internal.deployment;

import org.wso2.carbon.uiserver.api.App;
import org.wso2.carbon.uiserver.api.Configuration;
import org.wso2.carbon.uiserver.api.Extension;
import org.wso2.carbon.uiserver.api.I18nResource;
import org.wso2.carbon.uiserver.api.Page;
import org.wso2.carbon.uiserver.api.Theme;
import org.wso2.carbon.uiserver.api.UriPatten;
import org.wso2.carbon.uiserver.api.exception.RenderingException;
import org.wso2.carbon.uiserver.api.http.HttpRequest;
import org.wso2.carbon.uiserver.internal.deployment.parser.ConfigurationYaml;
import org.wso2.carbon.uiserver.internal.deployment.parser.YamlFileParser;
import org.wso2.carbon.uiserver.internal.exception.AppCreationException;
import org.wso2.carbon.uiserver.internal.exception.FileOperationException;
import org.wso2.carbon.uiserver.internal.impl.HbsPage;
import org.wso2.carbon.uiserver.internal.impl.HtmlPage;
import org.wso2.carbon.uiserver.internal.reference.AppReference;
import org.wso2.carbon.uiserver.internal.reference.ExtensionReference;
import org.wso2.carbon.uiserver.internal.reference.FileReference;
import org.wso2.carbon.uiserver.internal.reference.I18nResourceReference;
import org.wso2.carbon.uiserver.internal.reference.PageReference;
import org.wso2.carbon.uiserver.internal.reference.ThemeReference;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A creator that creates web apps.
 *
 * @since 0.8.0
 */
public class AppCreator {

    /**
     * Creates an app.
     *
     * @param appReference reference to the app
     * @param appContext   context path of the app
     * @return created app
     * @throws AppCreationException if an error occurred when creating the app
     */
    public static App createApp(AppReference appReference, String appContext) throws AppCreationException {
        try {
            SortedSet<Page> pages = createPages(appReference);
            Set<Extension> extensions = appReference.getExtensionReferences().stream()
                    .map(AppCreator::createExtension)
                    .collect(Collectors.toSet());
            Set<Theme> themes = appReference.getThemeReferences().stream()
                    .map(AppCreator::createTheme)
                    .collect(Collectors.toSet());
            Set<I18nResource> i18nResources = appReference.getI18nResourceReferences().stream()
                    .map(AppCreator::createI18nResource)
                    .collect(Collectors.toSet());
            Configuration configuration = appReference.getConfiguration()
                    .map(AppCreator::createConfiguration)
                    .orElse(Configuration.DEFAULT_CONFIGURATION);
            return new App(appReference.getName(), appContext, pages, extensions, themes, i18nResources, configuration,
                           appReference.getPath());
        } catch (FileOperationException e) {
            throw new AppCreationException(
                    "Cannot create web app '" + appReference.getName() + "' with context path '" + appContext + "'.",
                    e);
        }
    }

    private static SortedSet<Page> createPages(AppReference appReference) {
        List<Page> pages = appReference.getPageReferences().stream()
                .map(AppCreator::createPage)
                .collect(Collectors.toList());
        // TODO: 10/13/17 remove following workaround after adding support for URI patterns with * in UriPatten class
        if ((pages.size() == 1) && (pages.get(0).getUriPatten().matches("/index"))) {
            final Page indexPage = pages.get(0);
            pages.add(new Page(new UriPatten("/{+index}")) {
                @Override
                public String render(HttpRequest request, Configuration configuration) throws RenderingException {
                    return indexPage.render(request, configuration);
                }
            });
        }

        return new TreeSet<>(pages);
    }

    private static Page createPage(PageReference pageReference) {
        FileReference fileReference = pageReference.getHtmlFile();
        switch (fileReference.getExtension()) {
            case "html":
                return new HtmlPage(new UriPatten(pageReference.getPathPattern()), fileReference.getContent());
            case "hbs":
                return new HbsPage(new UriPatten(pageReference.getPathPattern()), fileReference.getContent());
            default:
                throw new AppCreationException("Found unsupported extension '" + fileReference.getExtension() +
                                               "' when creating a page for file '" + fileReference.getFilePath() +
                                               "'.");
        }
    }

    private static Extension createExtension(ExtensionReference extensionReference) {
        return new Extension(extensionReference.getName(), extensionReference.getType(), extensionReference.getPath());
    }

    private static Theme createTheme(ThemeReference themeReference) {
        return new Theme(themeReference.getName(), themeReference.getPath());
    }

    private static I18nResource createI18nResource(I18nResourceReference i18nResourceReference) {
        return new I18nResource(i18nResourceReference.getLocale(), i18nResourceReference.getMessages());
    }

    private static Configuration createConfiguration(FileReference fileReference) {
        ConfigurationYaml configurationYaml = YamlFileParser.parse(fileReference, ConfigurationYaml.class);
        return configurationYaml.toConfiguration();
    }
}
