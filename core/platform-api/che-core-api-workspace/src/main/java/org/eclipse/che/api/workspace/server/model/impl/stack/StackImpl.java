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
package org.eclipse.che.api.workspace.server.model.impl.stack;

import org.eclipse.che.api.core.model.workspace.WorkspaceConfig;
import org.eclipse.che.api.machine.server.recipe.GroupImpl;
import org.eclipse.che.api.machine.server.recipe.PermissionsImpl;
import org.eclipse.che.api.machine.shared.Group;
import org.eclipse.che.api.machine.shared.Permissions;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceConfigImpl;
import org.eclipse.che.api.workspace.server.model.stack.Stack;
import org.eclipse.che.api.workspace.server.model.stack.StackComponent;
import org.eclipse.che.api.workspace.server.model.stack.StackSource;
import org.eclipse.che.api.workspace.server.stack.image.StackIcon;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.lang.NameGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * Server implementation of {@link Stack}
 *
 * @author Alexander Andrienko
 */
public class StackImpl implements Stack {

    private String                   id;
    private String                   name;
    private String                   description;
    private String                   scope;
    private String                   creator;
    private List<String>             tags;
    private WorkspaceConfigImpl      workspaceConfig;
    private StackSourceImpl          source;
    private List<StackComponentImpl> components;
    private PermissionsImpl          permissions;
    private StackIcon                stackIcon;

    public static StackBuilder builder() {
        return new StackBuilder();
    }

    public StackImpl(StackImpl stack) {
        this(stack.getId(),
             stack.getName(),
             stack.getDescription(),
             stack.getScope(),
             stack.getCreator(),
             stack.getTags(),
             stack.getWorkspaceConfig(),
             stack.getSource(),
             stack.getComponents(),
             stack.getPermissions(),
             stack.getStackIcon());
    }

    public StackImpl(Stack stack) {
        this(stack.getId(),
             stack.getName(),
             stack.getDescription(),
             stack.getScope(),
             stack.getCreator(),
             stack.getTags(),
             stack.getWorkspaceConfig(),
             stack.getSource(),
             stack.getComponents(),
             stack.getPermissions(),
             null);
    }

    public StackImpl(String id,
                     String name,
                     @Nullable String description,
                     String scope,
                     String creator,
                     List<String> tags,
                     @Nullable WorkspaceConfig workspaceConfig,
                     @Nullable StackSource source,
                     @Nullable List<? extends StackComponent> components,
                     @Nullable Permissions permissions,
                     @Nullable StackIcon stackIcon) {
        this.id = requireNonNull(id, "Required non-null stack id");
        this.creator = requireNonNull(creator, "Required non-null stack creator");
        setName(name);
        setScope(scope);
        setTags(tags);
        setWorkspaceConfig(workspaceConfig == null ? null : new WorkspaceConfigImpl(workspaceConfig));
        setSource(source == null ? null : new StackSourceImpl(source));

        if (permissions != null) {
            List<Group> groups = permissions.getGroups()
                                            .stream()
                                            .map(group -> new GroupImpl(group.getName(), group.getUnit(), group.getAcl()))
                                            .collect(Collectors.toList());
            this.permissions = new PermissionsImpl(permissions.getUsers(), groups);
        }

        this.stackIcon = stackIcon;
        this.description = description;

        this.components = components == null ? new ArrayList<>() : components.stream()
                                                                             .map(StackComponentImpl::new)
                                                                             .collect(toList());

        if (source == null && workspaceConfig == null) {
            throw new IllegalArgumentException("Require non-null source: 'workspaceConfig' or 'stackSource'");
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        requireNonNull("require non-null stack name");
        this.name = name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        requireNonNull(scope, "Required non-null scope value: 'general' or 'advanced'");
        if (!scope.equals("general") && !scope.equals("advanced")) {
            throw new IllegalArgumentException("Stack scope must be 'general' or 'advanced'");
        }
        this.scope = scope;
    }

    @Override
    public String getCreator() {
        return creator;
    }

    @Override
    public List<String> getTags() {
        if (tags == null) {
            return new ArrayList<>();
        }
        return tags;
    }

    public void setTags(List<String> tags) {
        requireNonNull(tags, "Required non-null stack tags");
        if (tags.isEmpty()) {
            throw new IllegalArgumentException("List tags must be non empty");
        }
        this.tags = tags;
    }

    @Override
    public WorkspaceConfigImpl getWorkspaceConfig() {
        return workspaceConfig;
    }

    public void setWorkspaceConfig(WorkspaceConfigImpl workspaceConfig) {
        this.workspaceConfig = workspaceConfig;
    }

    @Override
    public StackSourceImpl getSource() {
        return source;
    }

    public void setSource(StackSourceImpl source) {
        this.source = source;
    }

    @Override
    public List<StackComponentImpl> getComponents() {
        if (components == null) {
            return new ArrayList<>();
        }
        return components;
    }

    public void setComponents(List<StackComponentImpl> components) {
        this.components = components;
    }

    public void setPermissions(PermissionsImpl permissions) {
        this.permissions = permissions;
    }

    @Override
    public PermissionsImpl getPermissions() {
        return permissions;
    }

    public StackIcon getStackIcon() {
        return stackIcon;
    }

    public void setStackIcon(StackIcon stackIcon) {
        this.stackIcon = stackIcon;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof StackImpl)) {
            return false;
        }
        StackImpl other = (StackImpl)obj;
        return Objects.equals(id, other.id) &&
               Objects.equals(name, other.name) &&
               Objects.equals(description, other.description) &&
               Objects.equals(creator, other.creator) &&
               Objects.equals(scope, other.scope) &&
               getTags().equals(other.getTags()) &&
               getComponents().equals(other.getComponents()) &&
               Objects.equals(workspaceConfig, other.workspaceConfig) &&
               Objects.equals(source, other.source) &&
               Objects.equals(permissions, other.permissions) &&
               Objects.equals(stackIcon, other.stackIcon);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + Objects.hashCode(id);
        hash = 31 * hash + Objects.hashCode(name);
        hash = 31 * hash + Objects.hashCode(description);
        hash = 31 * hash + Objects.hashCode(scope);
        hash = 31 * hash + Objects.hashCode(creator);
        hash = 31 * hash + getTags().hashCode();
        hash = 31 * hash + getComponents().hashCode();
        hash = 31 * hash + Objects.hashCode(workspaceConfig);
        hash = 31 * hash + Objects.hashCode(source);
        hash = 31 * hash + Objects.hashCode(permissions);
        hash = 31 * hash + Objects.hashCode(stackIcon);
        return hash;
    }

    @Override
    public String toString() {
        return "StackImpl{id='" + id +
               "', name='" + name +
               "', description='" + description +
               "', scope='" + scope +
               "', creator='" + creator +
               "', tags='" + tags +
               "', workspaceConfig='" + workspaceConfig +
               "', stackSource='" + source +
               "', components='" + components +
               "', permission='" + permissions +
               "', stackIcon='" + stackIcon +
               "'}";
    }

    public static class StackBuilder {

        private String                         id;
        private String                         name;
        private String                         description;
        private String                         scope;
        private String                         creator;
        private List<String>                   tags;
        private WorkspaceConfig                workspaceConfig;
        private StackSource                    source;
        private List<? extends StackComponent> components;
        private Permissions                    permissions;
        private StackIcon                      stackIcon;

        public StackBuilder generateId() {
            id = NameGenerator.generate("stack", 16);
            return this;
        }

        public StackBuilder setId(String id) {
            this.id = id;
            return this;
        }

        public StackBuilder setName(String name) {
            this.name = name;
            return this;
        }

        public StackBuilder setDescription(String description) {
            this.description = description;
            return this;
        }

        public StackBuilder setScope(String scope) {
            this.scope = scope;
            return this;
        }

        public StackBuilder setCreator(String creator) {
            this.creator = creator;
            return this;
        }

        public StackBuilder setTags(List<String> tags) {
            this.tags = (tags == null) ? new ArrayList<>() : tags;
            return this;
        }

        public StackBuilder setWorkspaceConfig(WorkspaceConfig workspaceConfig) {
            this.workspaceConfig = workspaceConfig;
            return this;
        }

        public StackBuilder setSource(StackSource source) {
            this.source = source;
            return this;
        }

        public StackBuilder setComponents(List<? extends StackComponent> components) {
            this.components = (components == null) ? new ArrayList<>() : components;
            return this;
        }

        public StackBuilder setPermissions(Permissions permissions) {
            this.permissions = permissions;
            return this;
        }

        public StackBuilder setStackIcon(StackIcon stackIcon) {
            this.stackIcon = stackIcon;
            return this;
        }

        public StackImpl build() {
            return new StackImpl(id, name, description, scope, creator, tags, workspaceConfig, source, components, permissions, stackIcon);
        }
    }
}
