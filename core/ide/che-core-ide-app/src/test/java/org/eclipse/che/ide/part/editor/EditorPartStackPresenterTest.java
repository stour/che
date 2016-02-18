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
package org.eclipse.che.ide.part.editor;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtmockito.GwtMockitoTestRunner;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.workspace.shared.dto.ProjectConfigDto;
import org.eclipse.che.ide.api.editor.AbstractEditorPresenter;
import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.api.editor.EditorInput;
import org.eclipse.che.ide.api.editor.EditorPartPresenter;
import org.eclipse.che.ide.api.editor.EditorWithErrors;
import org.eclipse.che.ide.api.event.project.CloseCurrentProjectEvent;
import org.eclipse.che.ide.api.event.project.CloseCurrentProjectHandler;
import org.eclipse.che.ide.api.parts.PropertyListener;
import org.eclipse.che.ide.api.project.tree.VirtualFile;
import org.eclipse.che.ide.part.widgets.TabItemFactory;
import org.eclipse.che.ide.part.PartStackPresenter.PartStackEventHandler;
import org.eclipse.che.ide.part.PartsComparator;
import org.eclipse.che.ide.part.widgets.editortab.EditorTab;
import org.eclipse.che.ide.part.widgets.listtab.ListButton;
import org.eclipse.che.ide.part.widgets.listtab.ListItem;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.vectomatic.dom.svg.ui.SVGResource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Dmitry Shnurenko
 */
@RunWith(GwtMockitoTestRunner.class)
public class EditorPartStackPresenterTest {

    private static final String SOME_TEXT = "someText";

    //constructor mocks
    @Mock
    private EditorPartStackView   view;
    @Mock
    private PartsComparator       partsComparator;
    @Mock
    private EventBus              eventBus;
    @Mock
    private TabItemFactory        tabItemFactory;
    @Mock
    private PartStackEventHandler partStackEventHandler;
    @Mock
    private ListButton            listButton;
    @Mock
    private Provider<EditorAgent> editorAgentProvider;

    //additional mocks
    @Mock
    private EditorTab                editorTab1;
    @Mock
    private EditorTab                editorTab2;
    @Mock
    private EditorWithErrors         withErrorsPart;
    @Mock
    private AbstractEditorPresenter  partPresenter1;
    @Mock
    private SVGResource              resource1;
    @Mock
    private AbstractEditorPresenter  partPresenter2;
    @Mock
    private SVGResource              resource2;
    @Mock
    private CloseCurrentProjectEvent closeProjectEvent;
    @Mock
    private ProjectConfigDto         descriptor;
    @Mock
    private EditorPartPresenter      editorPartPresenter;
    @Mock
    private EditorInput              editorInput1;
    @Mock
    private EditorInput              editorInput2;
    @Mock
    private VirtualFile              file1;
    @Mock
    private VirtualFile              file2;

    @Captor
    private ArgumentCaptor<ListItem>                   itemCaptor;
    @Captor
    private ArgumentCaptor<CloseCurrentProjectHandler> closeProjectHandlerCaptor;
    @Captor
    private ArgumentCaptor<AsyncCallback<Void>>        argumentCaptor;

    private EditorPartStackPresenter presenter;

    @Before
    public void setUp() {
        when(partPresenter1.getTitle()).thenReturn(SOME_TEXT);
        when(partPresenter1.getTitleSVGImage()).thenReturn(resource1);

        when(partPresenter2.getTitle()).thenReturn(SOME_TEXT);
        when(partPresenter2.getTitleSVGImage()).thenReturn(resource2);

        when(partPresenter1.getEditorInput()).thenReturn(editorInput1);
        when(editorInput1.getFile()).thenReturn(file1);

        when(partPresenter2.getEditorInput()).thenReturn(editorInput2);
        when(editorInput2.getFile()).thenReturn(file2);

        when(tabItemFactory.createEditorPartButton(file1, resource1, SOME_TEXT)).thenReturn(editorTab1);
        when(tabItemFactory.createEditorPartButton(file2, resource2, SOME_TEXT)).thenReturn(editorTab2);

        presenter = new EditorPartStackPresenter(view,
                                                 partsComparator,
                                                 eventBus,
                                                 tabItemFactory,
                                                 partStackEventHandler,
                                                 listButton);
    }

    @Test
    public void constructorShouldBeVerified() {
        verify(listButton).setDelegate(presenter);
        verify(view, times(2)).setDelegate(presenter);
        verify(view).setListButton(listButton);
    }

    @Test
    public void focusShouldBeSet() {
        presenter.setFocus(true);

        verify(view).setFocus(true);
    }

    @Test
    public void partShouldBeAdded() {
        presenter.addPart(partPresenter1);

        verify(partPresenter1, times(2)).addPropertyListener(Matchers.<PropertyListener>anyObject());

        verify(tabItemFactory).createEditorPartButton(file1, resource1, SOME_TEXT);

        verify(partPresenter1).getTitleSVGImage();
        verify(partPresenter1).getTitle();

        verify(editorTab1).setDelegate(presenter);

        verify(view).addTab(editorTab1, partPresenter1);

        verify(listButton).addListItem(Matchers.<ListItem>anyObject());

        verify(view).selectTab(partPresenter1);
    }

    @Test
    public void partShouldNotBeAddedWhenItExist() {
        presenter.addPart(partPresenter1);
        reset(view);

        presenter.addPart(partPresenter1);

        verify(view, never()).addTab(editorTab1, partPresenter1);

        verify(view).selectTab(partPresenter1);
    }

    @Test
    public void activePartShouldBeReturned() {
        presenter.setActivePart(partPresenter1);

        assertEquals(presenter.getActivePart(), partPresenter1);
    }

    @Test
    public void onTabShouldBeClicked() {
        presenter.addPart(partPresenter1);
        reset(view);

        presenter.onTabClicked(editorTab1);

        verify(view).selectTab(partPresenter1);
    }

    @Test
    public void tabShouldBeClosed() {
        presenter.addPart(partPresenter1);

        presenter.removePart(partPresenter1);
        presenter.onTabClose(editorTab1);

        verify(view).removeTab(partPresenter1);
    }

    @Test
    public void activePartShouldBeChangedWhenWeClickOnTab() {
        presenter.addPart(partPresenter1);
        presenter.addPart(partPresenter2);

        presenter.onTabClicked(editorTab1);

        assertEquals(presenter.getActivePart(), partPresenter1);

        presenter.onTabClicked(editorTab2);

        assertEquals(presenter.getActivePart(), partPresenter2);
    }

    @Test
    public void previousTabSelectedWhenWeRemovePart() {
        presenter.addPart(partPresenter1);
        presenter.addPart(partPresenter2);

        presenter.onTabClicked(editorTab2);
        presenter.removePart(partPresenter2);
        presenter.onTabClose(editorTab2);

        assertEquals(presenter.getActivePart(), partPresenter1);
    }

    @Test
    public void activePartShouldBeNullWhenWeCloseAllParts() {
        presenter.addPart(partPresenter1);

        presenter.onTabClose(editorTab1);

        assertThat(presenter.getActivePart(), is(nullValue()));
    }
}
