/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.workspace.server.stack;

import com.google.common.io.Resources;
import com.jayway.restassured.response.Response;

import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.ApiExceptionMapper;
import org.eclipse.che.api.core.rest.shared.dto.ServiceError;
import org.eclipse.che.api.machine.server.model.impl.CommandImpl;
import org.eclipse.che.api.machine.server.model.impl.LimitsImpl;
import org.eclipse.che.api.machine.server.model.impl.MachineConfigImpl;
import org.eclipse.che.api.machine.server.model.impl.MachineSourceImpl;
import org.eclipse.che.api.machine.server.recipe.PermissionsChecker;
import org.eclipse.che.api.machine.shared.Permissible;
import org.eclipse.che.api.machine.shared.dto.recipe.GroupDescriptor;
import org.eclipse.che.api.machine.shared.dto.recipe.PermissionsDescriptor;
import org.eclipse.che.api.workspace.server.model.stack.StackComponent;
import org.eclipse.che.api.workspace.server.spi.StackDao;
import org.eclipse.che.api.workspace.server.model.impl.EnvironmentImpl;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceConfigImpl;
import org.eclipse.che.api.workspace.server.model.impl.stack.StackImpl;
import org.eclipse.che.api.workspace.server.model.impl.stack.StackComponentImpl;
import org.eclipse.che.api.workspace.server.model.impl.stack.StackSourceImpl;
import org.eclipse.che.api.workspace.server.stack.image.StackIcon;
import org.eclipse.che.api.workspace.shared.dto.stack.StackDto;
import org.eclipse.che.api.workspace.shared.dto.stack.StackComponentDto;
import org.eclipse.che.api.workspace.shared.dto.stack.StackSourceDto;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.user.UserImpl;
import org.eclipse.che.dto.server.DtoFactory;
import org.everrest.assured.EverrestJetty;
import org.everrest.core.Filter;
import org.everrest.core.GenericContainerRequest;
import org.everrest.core.RequestFilter;
import org.everrest.core.impl.uri.UriBuilderImpl;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.ws.rs.core.UriInfo;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import static org.everrest.assured.JettyHttpServer.ADMIN_USER_NAME;
import static org.everrest.assured.JettyHttpServer.ADMIN_USER_PASSWORD;
import static org.everrest.assured.JettyHttpServer.SECURE_PATH;
import static org.eclipse.che.api.workspace.server.Constants.LINK_REL_REMOVE_STACK;
import static org.eclipse.che.api.workspace.server.Constants.LINK_REL_GET_STACK_BY_ID;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.util.Collections.singletonList;

/**
 * Test for {@link @StackService}
 *
 * @author Alexander Andrienko
 */
@Listeners(value = {EverrestJetty.class, MockitoTestNGListener.class})
public class StackServiceTest {

    private static final String STACK_ID        = "java-default";
    private static final String NAME            = "Java";
    private static final String DESCRIPTION     = "Default Java Stack with JDK 8, Maven and Tomcat.";
    private static final String USER_ID         = "che";
    private static final String CREATOR         = USER_ID;
    private static final String FOREIGN_CREATOR = "foreign_creator";
    private static final String SCOPE           = "general";

    private static final String SOURCE_TYPE   = "image";
    private static final String SOURCE_ORIGIN = "codenvy/ubuntu_jdk8";

    private static final String COMPONENT_NAME    = "Java";
    private static final String COMPONENT_VERSION = "1.8.0_45";

    private static final String WORKSPACE_CONFIG_NAME = "default";
    private static final String DEF_ENVIRONMENT_NAME  = "default";

    private static final String COMMAND_NAME = "newMaven";
    private static final String COMMAND_TYPE = "mvn";
    private static final String COMMAND_LINE = "mvn clean install -f ${current.project.path}";

    private static final String ENVIRONMENT_NAME = "default";

    private static final String  MACHINE_CONFIG_NAME = "ws-machine";
    private static final String  MACHINE_TYPE        = "docker";
    private static final boolean IS_DEV              = true;

    private static final String MACHINE_SOURCE_LOCATION = "http://localhost:8080/ide/api/recipe/recipe_ubuntu/script";
    private static final String MACHINE_SOURCE_TYPE     = "recipe";

    private static final String ICON_MEDIA_TYPE = "image/svg+xml";

    @SuppressWarnings("unused")
    static final   EnvironmentFilter  FILTER = new EnvironmentFilter();
    @SuppressWarnings("unused")
    static final   ApiExceptionMapper MAPPER = new ApiExceptionMapper();
    private static LinkedList<String> ROLES  = new LinkedList<>(Collections.singletonList("user"));

    private List<String> tags = asList("java", "maven");
    private StackDto              stackDto;
    private StackImpl             stackImpl;
    private StackImpl             foreignStack;
    private StackSourceImpl       stackSourceImpl;
    private List<StackComponent>  componentsImpl;
    private StackIcon             stackIcon;
    private PermissionsDescriptor permissionsDescriptor;

    private StackSourceDto          stackSourceDto;
    private List<StackComponentDto> componentsDto;

    @Mock
    StackDao stackDao;

    @Mock
    UriInfo uriInfo;

    @Mock
    StackComponentImpl stackComponent;
    @Mock
    PermissionsChecker checker;

    @InjectMocks
    StackService service;

    @BeforeClass
    public void setUp() throws IOException, ConflictException {
        byte[] fileContent = STACK_ID.getBytes();
        stackIcon = new StackIcon(ICON_MEDIA_TYPE, "image/svg+xml", fileContent);
        componentsImpl = Collections.singletonList(new StackComponentImpl(COMPONENT_NAME, COMPONENT_VERSION));
        stackSourceImpl = new StackSourceImpl(SOURCE_TYPE, SOURCE_ORIGIN);
        CommandImpl command = new CommandImpl(COMMAND_NAME, COMMAND_LINE, COMMAND_TYPE);
        MachineSourceImpl machineSource = new MachineSourceImpl(MACHINE_SOURCE_TYPE, MACHINE_SOURCE_LOCATION);
        int limitMemory = 1000;
        LimitsImpl limits = new LimitsImpl(limitMemory);
        MachineConfigImpl machineConfig = new MachineConfigImpl(IS_DEV, MACHINE_CONFIG_NAME, MACHINE_TYPE, machineSource, limits);
        EnvironmentImpl environment = new EnvironmentImpl(ENVIRONMENT_NAME, null, Collections.singletonList(machineConfig));

        WorkspaceConfigImpl workspaceConfig = WorkspaceConfigImpl.builder()
                                                                 .setName(WORKSPACE_CONFIG_NAME)
                                                                 .setDefaultEnv(DEF_ENVIRONMENT_NAME)
                                                                 .setCommands(Collections.singletonList(command))
                                                                 .setEnvironments(Collections.singletonList(environment))
                                                                 .build();

        stackSourceDto = newDto(StackSourceDto.class).withType(SOURCE_TYPE).withOrigin(SOURCE_ORIGIN);
        StackComponentDto stackComponentDto = newDto(StackComponentDto.class).withName(COMPONENT_NAME).withVersion(COMPONENT_VERSION);
        componentsDto = Collections.singletonList(stackComponentDto);

        stackDto = DtoFactory.getInstance().createDto(StackDto.class).withId(STACK_ID)
                             .withName(NAME)
                             .withDescription(DESCRIPTION)
                             .withScope(SCOPE)
                             .withCreator(CREATOR)
                             .withTags(tags)
                             .withSource(stackSourceDto)
                             .withComponents(componentsDto);

        stackImpl = StackImpl.builder().setId(STACK_ID)
                             .setName(NAME)
                             .setDescription(DESCRIPTION)
                             .setScope(SCOPE)
                             .setCreator(CREATOR)
                             .setTags(tags)
                             .setSource(stackSourceImpl)
                             .setComponents(componentsImpl)
                             .setWorkspaceConfig(workspaceConfig)
                             .setStackIcon(stackIcon)
                             .build();

        foreignStack = StackImpl.builder().setId(STACK_ID)
                                .setName(NAME)
                                .setDescription(DESCRIPTION)
                                .setScope(SCOPE)
                                .setCreator(FOREIGN_CREATOR)
                                .setTags(tags)
                                .setSource(stackSourceImpl)
                                .setComponents(componentsImpl)
                                .setWorkspaceConfig(workspaceConfig)
                                .setStackIcon(stackIcon)
                                .build();

        Map<String, List<String>> userPermission = new HashMap<>();
        userPermission.put(USER_ID, asList("read", "write"));
        GroupDescriptor group = newDto(GroupDescriptor.class).withName("user").withAcl(asList("read", "write", "search"));
        List<GroupDescriptor> groupPermission = singletonList(group);
        permissionsDescriptor = newDto(PermissionsDescriptor.class).withUsers(userPermission).withGroups(groupPermission);
    }

    @BeforeMethod
    public void setUpUriInfo() throws NoSuchFieldException, IllegalAccessException {
        when(uriInfo.getBaseUriBuilder()).thenReturn(new UriBuilderImpl());

        final Field uriField = service.getClass()
                                      .getSuperclass()
                                      .getDeclaredField("uriInfo");
        uriField.setAccessible(true);
        uriField.set(service, uriInfo);
    }

    @AfterMethod
    public void cleanUp() {
        ROLES.remove("system/admin");
        ROLES.remove("system/manager");
    }

    /** Create stack */

    @Test
    public void shouldThrowBadRequestExceptionWhenUserTryCreateNullStack() {
        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType(APPLICATION_JSON)
                                         .when()
                                         .post(SECURE_PATH + "/stack");

        assertEquals(response.getStatusCode(), 400);
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), "Stack required");
    }

    @Test
    public void shouldThrowBadRequestExceptionWhenUserTryCreateStackWithNonRequiredStackName() {
        stackDto.setName(null);

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType(APPLICATION_JSON)
                                         .body(stackDto)
                                         .when()
                                         .post(SECURE_PATH + "/stack");

        assertEquals(response.getStatusCode(), 400);
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), "Stack name required");
    }

    @Test
    public void shouldThrowBadRequestExceptionWhenUserTryCreateStackWithNonRequiredSource() {
        stackDto.setSource(null);
        stackDto.setWorkspaceConfig(null);

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType(APPLICATION_JSON)
                                         .body(stackDto)
                                         .when()
                                         .post(SECURE_PATH + "/stack");

        assertEquals(response.getStatusCode(), 400);
        String expectedErrorMessage = "Stack source required. You must specify stack source: 'workspaceConfig' or 'stackSource'";
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), expectedErrorMessage);
    }

    @Test
    public void newStackShouldBeCreatedForUser() throws ConflictException, ServerException {
        stackShouldBeCreated();
    }

    @Test
    public void nonSystemUserShouldNotCreateStackWithPublicPermissions() {
        stackDto.setPermissions(permissionsDescriptor);
        when(checker.hasPublicSearchPermission(any(PermissionsDescriptor.class))).thenReturn(true);

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType(APPLICATION_JSON)
                                         .body(stackDto)
                                         .when()
                                         .post(SECURE_PATH + "/stack");

        assertEquals(response.getStatusCode(), 403);
        String expectedErrorMessage = "User '" + USER_ID + "' doesn't has access to use 'public: search' permission";
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), expectedErrorMessage);
    }

    @Test
    public void newStackShouldBeCreatedForSystemAdminWhenPermissionNonNull() throws ConflictException, ServerException {
        ROLES.add("system/admin");

        stackDto.setPermissions(permissionsDescriptor);

        stackShouldBeCreated();
    }

    @Test
    public void newStackShouldBeCreatedForSystemManagerWhenPermissionNonNull() throws ConflictException, ServerException {
        ROLES.add("system/manager");

        stackDto.setPermissions(permissionsDescriptor);

        stackShouldBeCreated();
    }

    private void stackShouldBeCreated() throws ConflictException, ServerException {
        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType(APPLICATION_JSON)
                                         .body(stackDto)
                                         .when()
                                         .post(SECURE_PATH + "/stack");

        assertEquals(response.getStatusCode(), 201);

        verify(stackDao).create(any(StackImpl.class));

        final StackDto stackDtoDescriptor = unwrapDto(response, StackDto.class);

        assertEquals(stackDtoDescriptor.getName(), stackDto.getName());
        assertEquals(stackDtoDescriptor.getCreator(), USER_ID);
        assertEquals(stackDtoDescriptor.getDescription(), stackDto.getDescription());
        assertEquals(stackDtoDescriptor.getTags(), stackDto.getTags());

        assertEquals(stackDtoDescriptor.getComponents(), stackDto.getComponents());

        assertEquals(stackDtoDescriptor.getSource(), stackDto.getSource());

        assertEquals(stackDtoDescriptor.getScope(), stackDto.getScope());

        assertEquals(stackDtoDescriptor.getLinks().size(), 2);
        assertEquals(stackDtoDescriptor.getLinks().get(0).getRel(), LINK_REL_REMOVE_STACK);
        assertEquals(stackDtoDescriptor.getLinks().get(1).getRel(), LINK_REL_GET_STACK_BY_ID);
    }

    @Test
    public void shouldThrowBadRequestExceptionOnCreateStackWithEmptyBody() {
        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType(APPLICATION_JSON)
                                         .when()
                                         .post(SECURE_PATH + "/stack");

        assertEquals(response.getStatusCode(), 400);
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), "Stack required");
    }

    @Test
    public void shouldThrowBadRequestExceptionOnCreateStackWithEmptyName() {
        StackComponentDto stackComponentDto = newDto(StackComponentDto.class).withName("Java").withVersion("1.8.45");
        StackSourceDto stackSourceDto = newDto(StackSourceDto.class).withType("image").withOrigin("codenvy/ubuntu_jdk8");
        StackDto stackDto = newDto(StackDto.class).withId(USER_ID)
                                                  .withDescription("")
                                                  .withScope("Simple java stack for generation java projects")
                                                  .withTags(asList("java", "maven"))
                                                  .withCreator("che")
                                                  .withComponents(Collections.singletonList(stackComponentDto))
                                                  .withSource(stackSourceDto);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .contentType(APPLICATION_JSON)
                                   .body(stackDto)
                                   .when()
                                   .post(SECURE_PATH + "/stack");

        assertEquals(response.getStatusCode(), 400);
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), "Stack name required");
    }

    /** Get stack by id */

    @Test
    public void stackByIdShouldBeReturnedForStackOwner() throws NotFoundException, ServerException {
        when(stackDao.getById(STACK_ID)).thenReturn(stackImpl);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .get(SECURE_PATH + "/stack/" + STACK_ID);

        assertEquals(response.getStatusCode(), 200);
        StackDto result = unwrapDto(response, StackDto.class);
        assertEquals(result.getId(), stackImpl.getId());
        assertEquals(result.getName(), stackImpl.getName());
        assertEquals(result.getDescription(), stackImpl.getDescription());
        assertEquals(result.getScope(), stackImpl.getScope());
        assertEquals(result.getTags().get(0), stackImpl.getTags().get(0));
        assertEquals(result.getTags().get(1), stackImpl.getTags().get(1));
        assertEquals(result.getComponents().get(0).getName(), stackImpl.getComponents().get(0).getName());
        assertEquals(result.getComponents().get(0).getVersion(), stackImpl.getComponents().get(0).getVersion());
        assertEquals(result.getSource().getType(), stackImpl.getSource().getType());
        assertEquals(result.getSource().getOrigin(), stackImpl.getSource().getOrigin());
        assertEquals(result.getCreator(), stackImpl.getCreator());
    }

    @Test
    public void foreignStackShouldBeReturnedIfUserHasPermission() throws ServerException, NotFoundException {
        when(stackDao.getById(anyString())).thenReturn(foreignStack);
        when(checker.hasAccess(any(StackImpl.class), eq(USER_ID), eq("read"))).thenReturn(true);

        sendRequestAndGetForeignStackById();
    }

    @Test
    public void foreignStackShouldNotBeReturnedIfUserHasNotPermission() throws ServerException, NotFoundException {
        when(stackDao.getById(anyString())).thenReturn(foreignStack);
        when(checker.hasAccess(any(StackImpl.class), eq(USER_ID), eq("read"))).thenReturn(false);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .get(SECURE_PATH + "/stack/" + STACK_ID);

        assertEquals(response.getStatusCode(), 403);
        String expectedMessage = format("User '%s' doesn't has access to stack '%s'", USER_ID, stackImpl.getId());
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), expectedMessage);
    }

    @Test
    public void foreignStackByIdShouldBeReturnedForSystemAdmin() throws NotFoundException, ServerException {
        ROLES.add("system/admin");
        when(stackDao.getById(anyString())).thenReturn(foreignStack);
        when(checker.hasAccess(any(StackImpl.class), eq("not this admin"), eq("read"))).thenReturn(true);

        sendRequestAndGetForeignStackById();
    }

    @Test
    public void foreignStackByIdShouldBeReturnedForSystemAdmin2() throws NotFoundException, ServerException {
        ROLES.add("system/admin");
        when(stackDao.getById(anyString())).thenReturn(foreignStack);
        when(checker.hasAccess(any(StackImpl.class), eq("not this admin"), eq("read"))).thenReturn(false);

        sendRequestAndGetForeignStackById();
    }

    @Test
    public void foreignStackByIdShouldBeReturnedForSystemManager() throws NotFoundException, ServerException {
        ROLES.add("system/manager");
        when(stackDao.getById(anyString())).thenReturn(foreignStack);
        when(checker.hasAccess(any(StackImpl.class), eq("not this manager"), eq("read"))).thenReturn(true);
        when(stackDao.getById(anyString())).thenReturn(foreignStack);

        sendRequestAndGetForeignStackById();
    }

    @Test
    public void foreignStackByIdShouldBeReturnedForSystemManager2() throws NotFoundException, ServerException {
        ROLES.add("system/manager");
        when(stackDao.getById(anyString())).thenReturn(foreignStack);
        when(checker.hasAccess(any(StackImpl.class), eq("not this manager"), eq("read"))).thenReturn(false);

        sendRequestAndGetForeignStackById();
    }

    private void sendRequestAndGetForeignStackById() throws NotFoundException, ServerException {
        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .get(SECURE_PATH + "/stack/" + STACK_ID);

        assertEquals(response.getStatusCode(), 200);
        StackDto result = unwrapDto(response, StackDto.class);
        assertEquals(result.getId(), foreignStack.getId());
        assertEquals(result.getName(), foreignStack.getName());
        assertEquals(result.getDescription(), foreignStack.getDescription());
        assertEquals(result.getScope(), foreignStack.getScope());
        assertEquals(result.getTags().get(0), foreignStack.getTags().get(0));
        assertEquals(result.getTags().get(1), foreignStack.getTags().get(1));
        assertEquals(result.getComponents().get(0).getName(), foreignStack.getComponents().get(0).getName());
        assertEquals(result.getComponents().get(0).getVersion(), foreignStack.getComponents().get(0).getVersion());
        assertEquals(result.getSource().getType(), foreignStack.getSource().getType());
        assertEquals(result.getSource().getOrigin(), foreignStack.getSource().getOrigin());
        assertEquals(result.getCreator(), foreignStack.getCreator());
    }

    /** Update stack */

    @Test
    public void shouldThrowBadRequestExceptionWhenUserTryUpdateStackWithNonRequiredSource() {
        StackDto updatedStackDto = stackDto.withSource(null).withWorkspaceConfig(null);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .contentType(APPLICATION_JSON)
                                   .content(updatedStackDto)
                                   .when()
                                   .put(SECURE_PATH + "/stack/" + STACK_ID);

        assertEquals(response.getStatusCode(), 400);
        String expectedMessage = "Stack source required. You must specify stack source: 'workspaceConfig' or 'stackSource'";
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), expectedMessage);
    }

    @Test
    public void shouldThrowForbiddenExceptionWhenUserTryUpdatePredefinedStack()
            throws NotFoundException, ServerException, ConflictException {
        final String updateSuffix = " updated";
        final String newScope = "advanced";
        StackDto updatedStackDto = DtoFactory.getInstance().createDto(StackDto.class)
                                             .withId(STACK_ID)
                                             .withName(NAME + updateSuffix)
                                             .withDescription(DESCRIPTION + updateSuffix)
                                             .withScope(newScope)
                                             .withCreator(CREATOR + 1)
                                             .withTags(tags)
                                             .withSource(stackSourceDto)
                                             .withComponents(componentsDto);

        StackImpl updatedStack = StackImpl.builder().setId(STACK_ID)
                                          .setName(NAME + updateSuffix)
                                          .setDescription(DESCRIPTION + updateSuffix)
                                          .setScope(newScope)
                                          .setCreator(CREATOR + 1)
                                          .setTags(tags)
                                          .setSource(stackSourceImpl)
                                          .setComponents(componentsImpl)
                                          .setStackIcon(stackIcon)
                                          .build();

        when(stackDao.getById(STACK_ID)).thenReturn(updatedStack);
        when(checker.hasAccess(any(Permissible.class), eq(CREATOR), anyString())).thenReturn(false);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .contentType(APPLICATION_JSON)
                                   .content(updatedStackDto)
                                   .when()
                                   .put(SECURE_PATH + "/stack/" + STACK_ID);

        assertEquals(response.getStatusCode(), 403);
        String expectedMessage = format("User '%s' doesn't has access to update stack '%s'", USER_ID, stackImpl.getId());
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), expectedMessage);
        verify(stackDao).getById(STACK_ID);
        verify(stackDao, never()).update(any());
    }

    @Test
    public void StackShouldBeUpdated() throws NotFoundException, ServerException, ConflictException {
        final String updatedDescription = "some description";
        final String updatedScope = "advanced";
        StackDto updatedStackDto = DtoFactory.getInstance().createDto(StackDto.class)
                                             .withId(STACK_ID)
                                             .withName(NAME)
                                             .withDescription(updatedDescription)
                                             .withScope(updatedScope)
                                             .withCreator(CREATOR)
                                             .withTags(tags)
                                             .withSource(stackSourceDto)
                                             .withComponents(componentsDto);

        StackImpl updateStack = new StackImpl(stackImpl);
        updateStack.setDescription(updatedDescription);
        updateStack.setScope(updatedScope);

        when(checker.hasAccess(any(StackImpl.class), anyString(), eq("write"))).thenReturn(true);
        when(stackDao.getById(STACK_ID)).thenReturn(stackImpl).thenReturn(updateStack);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .contentType(APPLICATION_JSON)
                                   .content(updatedStackDto)
                                   .when()
                                   .put(SECURE_PATH + "/stack/" + STACK_ID);

        assertEquals(response.getStatusCode(), 200);
        StackDto result = unwrapDto(response, StackDto.class);

        assertEquals(result.getId(), updatedStackDto.getId());
        assertEquals(result.getName(), updatedStackDto.getName());
        assertEquals(result.getDescription(), updatedStackDto.getDescription());
        assertEquals(result.getScope(), updatedStackDto.getScope());
        assertEquals(result.getTags().get(0), updatedStackDto.getTags().get(0));
        assertEquals(result.getTags().get(1), updatedStackDto.getTags().get(1));
        assertEquals(result.getComponents().get(0).getName(), updatedStackDto.getComponents().get(0).getName());
        assertEquals(result.getComponents().get(0).getVersion(), updatedStackDto.getComponents().get(0).getVersion());
        assertEquals(result.getSource().getType(), updatedStackDto.getSource().getType());
        assertEquals(result.getSource().getOrigin(), updatedStackDto.getSource().getOrigin());
        assertEquals(result.getCreator(), updatedStackDto.getCreator());

        verify(stackDao).update(any());
        verify(stackDao).getById(STACK_ID);
    }

    @Test
    public void creatorShouldNotBeUpdated() throws ServerException, NotFoundException {
        StackDto updatedStackDto = DtoFactory.getInstance().createDto(StackDto.class)
                                             .withId(STACK_ID)
                                             .withName(NAME)
                                             .withDescription(DESCRIPTION)
                                             .withScope(SCOPE)
                                             .withCreator("creator changed")
                                             .withTags(tags)
                                             .withSource(stackSourceDto)
                                             .withComponents(componentsDto);

        when(checker.hasAccess(any(StackImpl.class), anyString(), eq("write"))).thenReturn(true);

        when(stackDao.getById(anyString())).thenReturn(foreignStack);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .contentType(APPLICATION_JSON)
                                   .content(updatedStackDto)
                                   .when()
                                   .put(SECURE_PATH + "/stack/" + STACK_ID);

        assertEquals(response.getStatusCode(), 200);
        StackDto result = unwrapDto(response, StackDto.class);

        assertEquals(result.getId(), updatedStackDto.getId());
        assertEquals(result.getName(), updatedStackDto.getName());
        assertEquals(result.getDescription(), updatedStackDto.getDescription());
        assertEquals(result.getScope(), updatedStackDto.getScope());
        assertEquals(result.getTags().get(0), updatedStackDto.getTags().get(0));
        assertEquals(result.getTags().get(1), updatedStackDto.getTags().get(1));
        assertEquals(result.getComponents().get(0).getName(), updatedStackDto.getComponents().get(0).getName());
        assertEquals(result.getComponents().get(0).getVersion(), updatedStackDto.getComponents().get(0).getVersion());
        assertEquals(result.getSource().getType(), updatedStackDto.getSource().getType());
        assertEquals(result.getSource().getOrigin(), updatedStackDto.getSource().getOrigin());
        assertEquals(result.getCreator(), FOREIGN_CREATOR);

        verify(stackDao).update(any());
        verify(stackDao).getById(STACK_ID);
    }

    @Test
    public void systemAdminShouldUpdateStackWithNonNullPermissionDescriptor() throws ServerException, NotFoundException {
        ROLES.add("system/admin");

        StackDto updatedStackDto = DtoFactory.getInstance().createDto(StackDto.class)
                                             .withId(STACK_ID)
                                             .withName(NAME)
                                             .withDescription(DESCRIPTION)
                                             .withScope(SCOPE)
                                             .withTags(tags)
                                             .withSource(stackSourceDto)
                                             .withComponents(componentsDto)
                                             .withPermissions(permissionsDescriptor);

        when(checker.hasAccess(any(StackImpl.class), anyString(), eq("write"))).thenReturn(true);

        when(stackDao.getById(anyString())).thenReturn(foreignStack);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .contentType(APPLICATION_JSON)
                                   .content(updatedStackDto)
                                   .when()
                                   .put(SECURE_PATH + "/stack/" + STACK_ID);

        assertEquals(response.getStatusCode(), 200);
        StackDto result = unwrapDto(response, StackDto.class);

        assertEquals(result.getId(), updatedStackDto.getId());
        assertEquals(result.getName(), updatedStackDto.getName());
        assertEquals(result.getDescription(), updatedStackDto.getDescription());
        assertEquals(result.getScope(), updatedStackDto.getScope());
        assertEquals(result.getTags().get(0), updatedStackDto.getTags().get(0));
        assertEquals(result.getTags().get(1), updatedStackDto.getTags().get(1));
        assertEquals(result.getComponents().get(0).getName(), updatedStackDto.getComponents().get(0).getName());
        assertEquals(result.getComponents().get(0).getVersion(), updatedStackDto.getComponents().get(0).getVersion());
        assertEquals(result.getSource().getType(), updatedStackDto.getSource().getType());
        assertEquals(result.getSource().getOrigin(), updatedStackDto.getSource().getOrigin());
        assertEquals(result.getCreator(), FOREIGN_CREATOR);

        verify(stackDao).update(any());
        verify(stackDao).getById(STACK_ID);
    }

    @Test
    public void stackOwnerShouldUpdateStackWithNonNullAndNonPublicPermissionDescriptor() throws ServerException, NotFoundException {
        StackDto updatedStackDto = DtoFactory.getInstance().createDto(StackDto.class)
                                             .withId(STACK_ID)
                                             .withName(NAME)
                                             .withDescription(DESCRIPTION)
                                             .withScope(SCOPE)
                                             .withTags(tags)
                                             .withSource(stackSourceDto)
                                             .withComponents(componentsDto)
                                             .withPermissions(permissionsDescriptor);

        when(checker.hasAccess(any(StackImpl.class), anyString(), eq("write"))).thenReturn(true);

        when(stackDao.getById(anyString())).thenReturn(stackImpl);
        when(checker.hasPublicSearchPermission(any())).thenReturn(false);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .contentType(APPLICATION_JSON)
                                   .content(updatedStackDto)
                                   .when()
                                   .put(SECURE_PATH + "/stack/" + STACK_ID);

        assertEquals(response.getStatusCode(), 200);
        StackDto result = unwrapDto(response, StackDto.class);

        assertEquals(result.getId(), updatedStackDto.getId());
        assertEquals(result.getName(), updatedStackDto.getName());
        assertEquals(result.getDescription(), updatedStackDto.getDescription());
        assertEquals(result.getScope(), updatedStackDto.getScope());
        assertEquals(result.getTags().get(0), updatedStackDto.getTags().get(0));
        assertEquals(result.getTags().get(1), updatedStackDto.getTags().get(1));
        assertEquals(result.getComponents().get(0).getName(), updatedStackDto.getComponents().get(0).getName());
        assertEquals(result.getComponents().get(0).getVersion(), updatedStackDto.getComponents().get(0).getVersion());
        assertEquals(result.getSource().getType(), updatedStackDto.getSource().getType());
        assertEquals(result.getSource().getOrigin(), updatedStackDto.getSource().getOrigin());
        assertEquals(result.getCreator(), USER_ID);

        verify(stackDao).update(any());
        verify(stackDao).getById(STACK_ID);
    }

    @Test
    public void userShouldNotUpdateForeignStackIfHeHasNotPermissionUpdateAcl() throws ServerException, NotFoundException {
        StackDto updatedStackDto = DtoFactory.getInstance().createDto(StackDto.class)
                                             .withId(STACK_ID)
                                             .withName(NAME)
                                             .withDescription(DESCRIPTION)
                                             .withScope(SCOPE)
                                             .withTags(tags)
                                             .withSource(stackSourceDto)
                                             .withComponents(componentsDto)
                                             .withPermissions(permissionsDescriptor);

        when(checker.hasAccess(any(StackImpl.class), anyString(), eq("write"))).thenReturn(true);
        when(checker.hasAccess(any(StackImpl.class), anyString(), eq("update_acl"))).thenReturn(false);

        when(stackDao.getById(anyString())).thenReturn(foreignStack);
        when(checker.hasPublicSearchPermission(any())).thenReturn(false);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .contentType(APPLICATION_JSON)
                                   .content(updatedStackDto)
                                   .when()
                                   .put(SECURE_PATH + "/stack/" + STACK_ID);

        assertEquals(response.getStatusCode(), 403);
        String expectedMessage = format("User '%s' doesn't has access to update stack '%s' permissions", USER_ID, stackImpl.getId());
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), expectedMessage);
        verify(stackDao).getById(STACK_ID);
        verify(stackDao, never()).update(any());
    }

    @Test
    public void userShouldNotUpdateForeignStackWithPublicSearchPermissions() throws ServerException, NotFoundException {
        StackDto updatedStackDto = DtoFactory.getInstance().createDto(StackDto.class)
                                             .withId(STACK_ID)
                                             .withName(NAME)
                                             .withDescription(DESCRIPTION)
                                             .withScope(SCOPE)
                                             .withTags(tags)
                                             .withSource(stackSourceDto)
                                             .withComponents(componentsDto)
                                             .withPermissions(permissionsDescriptor);

        when(checker.hasAccess(any(StackImpl.class), anyString(), eq("write"))).thenReturn(true);
        when(checker.hasAccess(any(StackImpl.class), anyString(), eq("update_acl"))).thenReturn(true);
        when(checker.hasPublicSearchPermission(any())).thenReturn(true);
        when(stackDao.getById(anyString())).thenReturn(foreignStack);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .contentType(APPLICATION_JSON)
                                   .content(updatedStackDto)
                                   .when()
                                   .put(SECURE_PATH + "/stack/" + STACK_ID);

        assertEquals(response.getStatusCode(), 403);
        String expectedMessage = format("User '%s' doesn't has access to use 'public: search' permission", USER_ID, stackImpl.getId());
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), expectedMessage);
        verify(stackDao).getById(STACK_ID);
        verify(stackDao, never()).update(any());
    }

    @Test
    public void userStackOwnerCanNotUpdateStackWithPublicSearchPermissions() throws NotFoundException, ServerException {
        StackDto updatedStackDto = DtoFactory.getInstance().createDto(StackDto.class)
                                             .withId(STACK_ID)
                                             .withName(NAME)
                                             .withDescription(DESCRIPTION)
                                             .withScope(SCOPE)
                                             .withTags(tags)
                                             .withSource(stackSourceDto)
                                             .withComponents(componentsDto)
                                             .withPermissions(permissionsDescriptor);

        when(checker.hasAccess(any(StackImpl.class), anyString(), eq("write"))).thenReturn(true);

        when(stackDao.getById(anyString())).thenReturn(stackImpl);
        when(checker.hasPublicSearchPermission(any())).thenReturn(true);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .contentType(APPLICATION_JSON)
                                   .content(updatedStackDto)
                                   .when()
                                   .put(SECURE_PATH + "/stack/" + STACK_ID);

        assertEquals(response.getStatusCode(), 403);
        String expectedMessage = format("User '%s' doesn't has access to use 'public: search' permission", USER_ID);
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), expectedMessage);
        verify(stackDao).getById(STACK_ID);
        verify(stackDao, never()).update(any());
    }

    /** Delete stack */

    @Test
    public void stackShouldBeDeletedByStackOwner() throws ServerException, NotFoundException {
        when(stackDao.getById(STACK_ID)).thenReturn(stackImpl);
        when(checker.hasAccess(any(StackImpl.class), anyString(), eq("write"))).thenReturn(true);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .delete(SECURE_PATH + "/stack/" + STACK_ID);

        verify(stackDao).getById(STACK_ID);
        verify(stackDao).remove(any());
        assertEquals(response.getStatusCode(), 204);
    }

    @Test
    public void adminShouldDeleteStackForeignStack() throws ServerException, NotFoundException {
        ROLES.add("system/admin");
        when(stackDao.getById(STACK_ID)).thenReturn(foreignStack);
        when(checker.hasAccess(any(StackImpl.class), anyString(), eq("write"))).thenReturn(true);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .delete(SECURE_PATH + "/stack/" + STACK_ID);

        verify(stackDao).getById(STACK_ID);
        verify(stackDao).remove(any());
        assertEquals(response.getStatusCode(), 204);
    }

    @Test
    public void shouldThrowForbiddenExceptionWhenWeTryDeleteAlienStack() throws NotFoundException, ServerException {
        StackImpl stack = StackImpl.builder()
                                   .setId(STACK_ID)
                                   .setName(NAME)
                                   .setDescription(DESCRIPTION)
                                   .setScope(SCOPE)
                                   .setCreator("someUser")
                                   .setTags(tags)
                                   .setSource(stackSourceImpl)
                                   .setComponents(componentsImpl)
                                   .setStackIcon(stackIcon)
                                   .build();

        when(stackDao.getById(STACK_ID)).thenReturn(stack);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .delete(SECURE_PATH + "/stack/" + STACK_ID);

        verify(stackDao).getById(STACK_ID);
        assertEquals(response.getStatusCode(), 403);
        String expectedErrorMessage = format("User '%s' doesn't has access to stack with id '%s'", USER_ID, STACK_ID);
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), expectedErrorMessage);
    }

    /** Get stacks by creator */

    @Test
    public void createdStacksShouldBeReturned() throws ServerException {
        when(stackDao.getByCreator(USER_ID, 0, 1)).thenReturn(Collections.singletonList(stackImpl));

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .get(SECURE_PATH + "/stack?skipCount=0&maxItems=1");

        assertEquals(response.getStatusCode(), 200);
        StackDto result = DtoFactory.getInstance()
                                    .createListDtoFromJson(response.body().print(), StackDto.class)
                                    .get(0);

        assertEquals(result.getId(), stackImpl.getId());
        assertEquals(result.getName(), stackImpl.getName());
        assertEquals(result.getDescription(), stackImpl.getDescription());
        assertEquals(result.getScope(), stackImpl.getScope());
        assertEquals(result.getTags().get(0), stackImpl.getTags().get(0));
        assertEquals(result.getTags().get(1), stackImpl.getTags().get(1));
        assertEquals(result.getComponents().get(0).getName(), stackImpl.getComponents().get(0).getName());
        assertEquals(result.getComponents().get(0).getVersion(), stackImpl.getComponents().get(0).getVersion());
        assertEquals(result.getSource().getType(), stackImpl.getSource().getType());
        assertEquals(result.getSource().getOrigin(), stackImpl.getSource().getOrigin());
        assertEquals(result.getCreator(), stackImpl.getCreator());
        verify(stackDao).getByCreator(USER_ID, 0, 1);
    }

    @Test
    public void shouldBeReturnedStackList() throws ServerException {
        when(stackDao.getByCreator(anyString(), anyInt(), anyInt())).thenReturn(singletonList(stackImpl));

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .get(SECURE_PATH + "/stack/");

        verify(stackDao).getByCreator(anyString(), anyInt(), anyInt());
        assertEquals(response.getStatusCode(), 200);
        List<StackDto> result = unwrapListDto(response, StackDto.class);
        assertEquals(result.get(0).getId(), stackImpl.getId());
        assertEquals(result.get(0).getName(), stackImpl.getName());
        assertEquals(result.get(0).getDescription(), stackImpl.getDescription());
        assertEquals(result.get(0).getScope(), stackImpl.getScope());
        assertEquals(result.get(0).getTags().get(0), stackImpl.getTags().get(0));
        assertEquals(result.get(0).getTags().get(1), stackImpl.getTags().get(1));
        assertEquals(result.get(0).getComponents().get(0).getName(), stackImpl.getComponents().get(0).getName());
        assertEquals(result.get(0).getComponents().get(0).getVersion(), stackImpl.getComponents().get(0).getVersion());
        assertEquals(result.get(0).getSource().getType(), stackImpl.getSource().getType());
        assertEquals(result.get(0).getSource().getOrigin(), stackImpl.getSource().getOrigin());
        assertEquals(result.get(0).getCreator(), stackImpl.getCreator());

        verify(stackDao).getByCreator(anyString(), anyInt(), anyInt());
    }

    /** Search stack by tags*/
    @Test
    public void shouldReturnsAllStacksWhenListTagsIsEmpty() throws ServerException {
        StackImpl stack2 = new StackImpl(stackImpl);
        stack2.setTags(singletonList("subversion"));
        List<StackImpl> stacks = asList(stackImpl, stack2);
        when(stackDao.searchStacks(anyList(), anyInt(), anyInt())).thenReturn(stacks);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .get(SECURE_PATH + "/stack/list");

        verify(stackDao).searchStacks(anyList(), anyInt(), anyInt());
        assertEquals(response.getStatusCode(), 200);

        List<StackDto> result = unwrapListDto(response, StackDto.class);
        assertEquals(result.size(), 2);
        assertEquals(result.get(0).getName(), stackImpl.getName());
        assertEquals(result.get(1).getName(), stack2.getName());
    }

    @Test
    public void shouldReturnsStackByTagList() throws ServerException {
        StackImpl stack2 = new StackImpl(stackImpl);
        stack2.setTags(singletonList("Subversion"));
        when(stackDao.searchStacks(eq(singletonList("Subversion")), anyInt(), anyInt())).thenReturn(singletonList(stack2));

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .get(SECURE_PATH + "/stack/list?tags=Subversion");

        verify(stackDao).searchStacks(eq(singletonList("Subversion")), anyInt(), anyInt());
        assertEquals(response.getStatusCode(), 200);

        List<StackDto> result = unwrapListDto(response, StackDto.class);
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getName(), stack2.getName());
    }

    /** Get icon by stack id */
    @Test
    public void shouldReturnIconByStackId() throws NotFoundException, ServerException {
        when(stackDao.getById(stackImpl.getId())).thenReturn(stackImpl);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .get(SECURE_PATH + "/stack/" + stackImpl.getId() + "/icon");
        assertEquals(response.getStatusCode(), 200);

        verify(stackDao).getById(stackImpl.getId());
    }

    @Test
    public void shouldThrowNotFoundExceptionWhenIconStackWasNotFound() throws NotFoundException, ServerException {
        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .get(SECURE_PATH + "/stack/" + stackImpl.getId() + "/icon");

        assertEquals(response.getStatusCode(), 404);
        String expectedErrorMessage = format("Stack with id '%s' was not found.", STACK_ID);
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), expectedErrorMessage);
        verify(stackDao).getById(stackImpl.getId());
    }

    @Test
    public void shouldThrowNotFoundExceptionWhenIconWasNotFound() throws NotFoundException, ServerException {
        StackImpl test = new StackImpl(stackImpl);
        test.setStackIcon(null);
        when(stackDao.getById(test.getId())).thenReturn(test);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .get(SECURE_PATH + "/stack/" + stackImpl.getId() + "/icon");

        assertEquals(response.getStatusCode(), 404);
        String expectedErrorMessage = format("Image for stack with id '%s' was not found.", STACK_ID);
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), expectedErrorMessage);
        verify(stackDao).getById(test.getId());
    }

    /** Delete icon by stack id */
    @Test
    public void stackIconShouldBeDeletedForUserOwner() throws NotFoundException, ServerException {
        when(stackDao.getById(stackImpl.getId())).thenReturn(stackImpl);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .delete(SECURE_PATH + "/stack/" + stackImpl.getId() + "/icon");

        assertEquals(response.getStatusCode(), 204);
        verify(stackDao).getById(stackImpl.getId());
        verify(stackDao).update(stackImpl);
    }

    @Test
    public void foreignStackIconShouldBeDeletedForAdmin() throws NotFoundException, ServerException {
        ROLES.add("system/admin");
        when(stackDao.getById(foreignStack.getId())).thenReturn(foreignStack);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .delete(SECURE_PATH + "/stack/" + foreignStack.getId() + "/icon");

        assertEquals(response.getStatusCode(), 204);
        verify(stackDao).getById(stackImpl.getId());
        verify(stackDao).update(any());
    }

    @Test
    public void stackIconSholdNotBeDeletedForForeignUserWithoutPermissions() throws NotFoundException, ServerException {
        when(checker.hasAccess(foreignStack, USER_ID, "write")).thenReturn(false);
        when(stackDao.getById(foreignStack.getId())).thenReturn(foreignStack);
        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .delete(SECURE_PATH + "/stack/" + foreignStack.getId() + "/icon");

        String expectedErrorMessage = format("User '%s' doesn't has access to stack with id '%s'", USER_ID, STACK_ID);
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), expectedErrorMessage);
        assertEquals(response.getStatusCode(), 403);
        verify(stackDao).getById(foreignStack.getId());
        verify(stackDao, never()).update(foreignStack);
    }


    @Test
    public void stackIconShouldBeDeletedIfUserHasPermissions() throws ServerException, NotFoundException {
        when(checker.hasAccess(foreignStack, USER_ID, "write")).thenReturn(true);
        when(stackDao.getById(foreignStack.getId())).thenReturn(foreignStack);
        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .delete(SECURE_PATH + "/stack/" + foreignStack.getId() + "/icon");
        assertEquals(response.getStatusCode(), 204);
        verify(stackDao).getById(foreignStack.getId());
        verify(stackDao).update(foreignStack);
    }

    /** Update stack icon */

    @Test
    public void stackIconShouldBeUploadedForUserOwner() throws NotFoundException, ServerException, URISyntaxException {
        File file = new File(Resources.getResource("stack_img").getPath(), "type-java.svg");

        when(stackDao.getById(stackImpl.getId())).thenReturn(stackImpl);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .multiPart("type-java.svg", file, "image/svg+xml")
                                   .contentType(MULTIPART_FORM_DATA)
                                   .post(SECURE_PATH + "/stack/" + stackImpl.getId() + "/icon");

        assertEquals(response.getStatusCode(), 200);
        verify(stackDao).getById(stackImpl.getId());
        verify(stackDao).update(any());
    }

    @Test
    public void foreignStackIconShouldBeUploadedForAdmin() throws NotFoundException, ServerException {
        ROLES.add("system/admin");
        File file = new File(Resources.getResource("stack_img").getPath(), "type-java.svg");
        when(stackDao.getById(foreignStack.getId())).thenReturn(foreignStack);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .multiPart("type-java.svg", file, "image/svg+xml")
                                   .contentType(MULTIPART_FORM_DATA)
                                   .post(SECURE_PATH + "/stack/" + stackImpl.getId() + "/icon");

        assertEquals(response.getStatusCode(), 200);
        verify(stackDao).getById(foreignStack.getId());
        verify(stackDao).update(any());
    }

    @Test
    public void foreignStackIconShouldBeUploadedForUserWithPermissions() throws NotFoundException, ServerException {
        File file = new File(Resources.getResource("stack_img").getPath(), "type-java.svg");
        when(stackDao.getById(foreignStack.getId())).thenReturn(foreignStack);
        when(checker.hasAccess(any(), any(), any())).thenReturn(true);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .multiPart("type-java.svg", file, "image/svg+xml")
                                   .contentType(MULTIPART_FORM_DATA)
                                   .post(SECURE_PATH + "/stack/" + stackImpl.getId() + "/icon");

        assertEquals(response.getStatusCode(), 200);
        verify(stackDao).getById(foreignStack.getId());
        verify(stackDao).update(any());
    }

    @Test
    public void stackIconShouldNotBeUploadedForUserWithoutPermissions() throws NotFoundException, ServerException {
        File file = new File(Resources.getResource("stack_img").getPath(), "type-java.svg");
        when(stackDao.getById(foreignStack.getId())).thenReturn(foreignStack);
        when(checker.hasAccess(any(), any(), any())).thenReturn(false);

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .when()
                                   .multiPart("type-java.svg", file, "image/svg+xml")
                                   .contentType(MULTIPART_FORM_DATA)
                                   .post(SECURE_PATH + "/stack/" + stackImpl.getId() + "/icon");

        assertEquals(response.getStatusCode(), 403);
        String expectedErrorMessage = format("User '%s' doesn't has access to stack with id '%s'", USER_ID, STACK_ID);
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), expectedErrorMessage);
        verify(stackDao).getById(foreignStack.getId());
        verify(stackDao, never()).update(any());
    }

    private static <T> T unwrapDto(Response response, Class<T> dtoClass) {
        return DtoFactory.getInstance().createDtoFromJson(response.body().print(), dtoClass);
    }

    private static <T> List<T> unwrapListDto(Response response, Class<T> dtoClass) {
        return DtoFactory.getInstance().createListDtoFromJson(response.body().print(), dtoClass);
    }

    @Filter
    public static class EnvironmentFilter implements RequestFilter {
        public void doFilter(GenericContainerRequest request) {
            EnvironmentContext.getCurrent().setUser(new UserImpl("user", USER_ID, "token", ROLES, false));
        }
    }
}
