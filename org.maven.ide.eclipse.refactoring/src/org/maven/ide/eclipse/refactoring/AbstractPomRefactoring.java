/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.refactoring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.emf.common.command.BasicCommandStack;
import org.eclipse.emf.common.command.CompoundCommand;
import org.eclipse.emf.common.notify.impl.AdapterFactoryImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.edit.domain.AdapterFactoryEditingDomain;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.edit.provider.ReflectiveItemProviderAdapterFactory;
import org.eclipse.emf.edit.provider.resource.ResourceItemProviderAdapterFactory;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.refactoring.changes.RenameJavaProjectChange;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.maven.ide.components.pom.Model;
import org.maven.ide.components.pom.Properties;
import org.maven.ide.components.pom.PropertyPair;
import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.core.MavenLogger;
import org.maven.ide.eclipse.project.IMavenProjectFacade;
import org.maven.ide.eclipse.refactoring.RefactoringModelResources.PropertyInfo;

/**
 * Base class for all pom.xml refactorings in workspace
 * 
 * @author Anton Kraev
 */
@SuppressWarnings("restriction")
public abstract class AbstractPomRefactoring extends Refactoring {

  // main file that is being refactored
  protected IFile file;
  
  // maven plugin
  MavenPlugin mavenPlugin;

  // editing domain
  private AdapterFactoryEditingDomain editingDomain;

  public AbstractPomRefactoring(IFile file) {
    this.file = file;
    
    this.mavenPlugin = MavenPlugin.getDefault();

    List<AdapterFactoryImpl> factories = new ArrayList<AdapterFactoryImpl>();
    factories.add(new ResourceItemProviderAdapterFactory());
    factories.add(new ReflectiveItemProviderAdapterFactory());

    ComposedAdapterFactory adapterFactory = new ComposedAdapterFactory(factories);
    BasicCommandStack commandStack = new BasicCommandStack();
    this.editingDomain = new AdapterFactoryEditingDomain(adapterFactory, //
        commandStack, new HashMap<Resource, Boolean>());
  }

  //this gets actual refactoring visitor
  public abstract PomVisitor getVisitor();
  
  @Override
  public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {
    return new RefactoringStatus();
  }
  
  @Override
  public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
    CompositeChange res = new CompositeChange("Renaming " + file.getParent().getName());
    IMavenProjectFacade[] projects = mavenPlugin.getMavenProjectManager().getProjects();
    pm.beginTask("Refactoring", projects.length);
    
    Map<String, RefactoringModelResources> models = new HashMap<String, RefactoringModelResources>();
    
    try {
      //load all models
      //XXX: assumption: artifactId is unique within workspace
      for(IMavenProjectFacade projectFacade : projects) {
        pm.setTaskName("Loading " + projectFacade.getProject().getName());
        RefactoringModelResources current = new RefactoringModelResources(projectFacade);
        models.put(current.effective.getArtifactId(), current);
        pm.worked(1);
      }
      
      //construct properties for all models
      for (String artifact: models.keySet()) {
        RefactoringModelResources model = models.get(artifact);
        Map<String, PropertyInfo> properties = new HashMap<String, PropertyInfo>();
        
        //find all workspace parents
        List<RefactoringModelResources> workspaceParents = new ArrayList<RefactoringModelResources>();
        MavenProject current = model.getProject();
        //add itself
        workspaceParents.add(model);
        while (current.getParent() != null) {
          MavenProject parentProject = current.getParent();
          String id = parentProject.getArtifactId();
          RefactoringModelResources parent = models.get(id);
          if (parent != null) {
            workspaceParents.add(parent);
          } else {
            break;
          }
          current = parentProject;
        }
        
        //fill properties (from the root)
        for (int i=workspaceParents.size() - 1; i >= 0; i--) {
          RefactoringModelResources resource = workspaceParents.get(i);
          Properties props = resource.getTmpModel().getProperties();
          if (props == null)
            continue;
          Iterator<?> it = props.getProperty().iterator();
          while (it.hasNext()) {
            PropertyPair pair = (PropertyPair) it.next();
            String pName = pair.getKey();
            PropertyInfo info = properties.get(pName);
            if (info == null) {
              info = new PropertyInfo();
              properties.put(pName, info);
            }
            info.setPair(pair);
            info.setResource(resource);
          }
        }
        
        model.setProperties(properties);
      }

      //calculate the list of affected models
      for (String artifact: models.keySet()) {
        RefactoringModelResources model = models.get(artifact);
        model.setCommand(getVisitor().applyChanges(editingDomain, file, model));
      }
      
      //process all refactored properties, creating more commands
      for (String artifact: models.keySet()) {
        RefactoringModelResources model = models.get(artifact);
        for (String pName: model.getProperties().keySet()) {
          PropertyInfo info = model.getProperties().get(pName);
          if (info.getNewValue() != null) {
            CompoundCommand command = info.getResource().getCommand();
            if (command == null) {
              command = new CompoundCommand();
              info.getResource().setCommand(command);
            }
            command.append(info.getNewValue());
          }
        }
      }
      
      for (String artifact: models.keySet()) {
        RefactoringModelResources model = models.get(artifact);
        CompoundCommand command = model.getCommand();
        if (command == null)
          continue;
        if (command.canExecute()) {
          //apply changes to temp file
          editingDomain.getCommandStack().execute(command);
          //create text change comparing temp file and real file
          TextFileChange change = new ChangeCreator(model.getPomFile(), model.getPomBuffer().getDocument(), model.getTmpBuffer().getDocument(), file.getParent().getName()).createChange();
          res.add(change);
        }
      }

      //rename project if required
      String newName = getNewProjectName(); 
      if (newName != null) {
        res.add(new RenameJavaProjectChange(JavaCore.create(file.getProject()), newName, true));
      }
    } catch(Exception ex) {
      MavenLogger.log("Problems during refactoring", ex);
    } finally {
      for (String artifact: models.keySet()) {
        models.get(artifact).releaseAllResources();
      }
    }
    
    return res;
  }

  //returns new eclipse project name or null if no change
  public String getNewProjectName() {
    return null;
  }
  
  protected IFile getFile() {
    return file;
  }

  public Model getModel() {
    try {
      return RefactoringModelResources.loadModel(file);
    } catch(CoreException ex) {
      MavenLogger.log("Problems during refactoring", ex);
      return null;
    }
  }
}
