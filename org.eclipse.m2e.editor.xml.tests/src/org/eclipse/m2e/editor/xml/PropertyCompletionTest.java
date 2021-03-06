/*******************************************************************************
 * Copyright (c) 2008-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.m2e.editor.xml;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.ResolverConfiguration;
import org.eclipse.wst.sse.ui.internal.contentassist.ContentAssistUtils;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMNode;

/**
 * Hello fellow tester:
 * everytime this test finds a regression add an 'x' here:
 * everytime you do mindless test update add an 'y' here: yy
 * @author mkleint
 *
 */

public class PropertyCompletionTest extends AbstractCompletionTest {
  
  IProject[] projects;
  protected IFile loadProjectsAndFiles() throws Exception {
    //Create the projects
    projects = importProjects("projects/MNGECLIPSE-2576", 
        new String[] {
        "parent2576/pom.xml",
        "child2576/pom.xml"
        }, new ResolverConfiguration());
    waitForJobsToComplete();
    return (IFile) projects[1].findMember("pom.xml");
  }

  public void testCompletion() throws Exception {
    //Get the location of the place where we want to start the completion
    String docString = sourceViewer.getDocument().get();
    IMavenProjectFacade facade = MavenPlugin.getMavenProjectRegistry().getProject(projects[1]);
    assertNotNull(facade);
    assertNotNull(facade.getMavenProject());
    sourceViewer.setMavenProject(facade.getMavenProject());
    
    
    int offset = docString.indexOf("${") + "${".length();
    
    IDOMNode node = (IDOMNode) ContentAssistUtils.getNodeAt(sourceViewer, offset);
    assertEquals("anotherProperty", node.getLocalName());

    ICompletionProposal[] proposals = getProposals(offset);
    assertTrue("Length less than 1", proposals.length > 1);
    assertEquals(InsertExpressionProposal.class, proposals[0].getClass());
    assertEquals("${aProperty}", ((InsertExpressionProposal) proposals[0]).getDisplayString());
  }

}
