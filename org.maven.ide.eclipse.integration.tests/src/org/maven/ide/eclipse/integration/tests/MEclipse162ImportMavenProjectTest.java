/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.integration.tests;

import java.io.File;


/**
 * @author Rich Seddon
 */
public class MEclipse162ImportMavenProjectTest extends UIIntegrationTestCase {

  private File tempDir;

  public void testSimpleModuleImport() throws Exception {
    tempDir = doImport("projects/commons-collections-3.2.1-src.zip");
  }

  public void testMultiModuleImport() throws Exception {
    tempDir = doImport("projects/httpcomponents-core-4.0-beta3-src.zip");
  }

  public void testMNGEclipse1028ImportOrderMatters() throws Exception {
    checkoutProjectsFromSVN("http://svn.sonatype.org/m2eclipse/trunk/org.maven.ide.eclipse.wtp.tests/projects/import-order-matters/");
    assertProjectsHaveNoErrors();
  }
  
  protected void tearDown() throws Exception {
    clearProjects();

    if(tempDir != null && tempDir.exists()) {
      deleteDirectory(tempDir);
      tempDir = null;
    }
    super.tearDown();

  }

}