/*
 * Copyright 2010 Henry Coles
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and limitations under the License. 
 */
package org.pitest.junit;

import static org.pitest.util.Unchecked.translateCheckedException;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.internal.builders.AllDefaultPossibilitiesBuilder;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.pitest.TestMethod;
import org.pitest.extension.ResultCollector;
import org.pitest.extension.TestUnit;
import org.pitest.internal.IsolationUtils;
import org.pitest.reflection.Reflection;

public class RunnerAdapter implements Serializable {

  private static final long                serialVersionUID = 1L;

  private transient Runner                 runner;
  private transient Map<String, Throwable> results;
  private transient List<TestUnit>         tus              = new ArrayList<TestUnit>();

  private final Class<?>                   clazz;

  public RunnerAdapter(final Class<?> clazz, final Runner runner) {
    this.clazz = clazz;
    this.runner = runner;
    gatherTestUnits(this.tus, this.runner.getDescription());
  }

  public RunnerAdapter(final Class<?> clazz) {
    this(clazz, createRunner(clazz));
  }

  private static Runner createRunner(final Class<?> clazz) {
    final AllDefaultPossibilitiesBuilder builder = // new AnnotatedBuilder(
    new AllDefaultPossibilitiesBuilder(true);// );
    try {
      return builder.runnerForClass(clazz);
    } catch (final Throwable ex) {
      throw translateCheckedException(ex);
    }
  }

  public List<TestUnit> getTestUnits() {
    return this.tus;
  }

  private void gatherTestUnits(final List<TestUnit> tus, final Description d) {
    if (d.isTest()) {
      tus.add(descriptionToTestUnit(d));
    } else {
      for (final Description each : d.getChildren()) {
        gatherTestUnits(tus, each);
      }
    }
  }

  private TestUnit descriptionToTestUnit(final Description d) {

    Class<?> descriptionTestClass;
    try {
      // it would be nice is junit could use the context class loader . . .
      descriptionTestClass = Class.forName(d.getClassName(), true, Thread
          .currentThread().getContextClassLoader());
    } catch (final ClassNotFoundException e) {
      descriptionTestClass = null;
    }

    final Method m = Reflection.publicMethod(descriptionTestClass, d
        .getMethodName());
    final TestMethod tm = new TestMethod(m, null);
    final org.pitest.Description pitDescription = new org.pitest.Description(d
        .getDisplayName(), d.getTestClass(), tm);
    return new RunnerAdapterTestUnit(this, d, pitDescription, null);
  }

  public void execute(final ClassLoader loader,
      final RunnerAdapterTestUnit testUnit, final ResultCollector rc) {
    rc.notifyStart(testUnit.description());
    runIfRequired(loader);
    notify(testUnit, rc);
  }

  private void notify(final RunnerAdapterTestUnit testUnit,
      final ResultCollector rc) {
    final Throwable t = this.results.get(CustomRunnerExecutor
        .descriptionToString(testUnit.getJunitDescription()));
    if (t != null) {
      // TODO translate the throwable + isn't there a potential classloader leak
      // here?
      rc.notifyEnd(testUnit.description(), t);
    } else {
      rc.notifyEnd(testUnit.description());
    }

  }

  @SuppressWarnings("unchecked")
  private void runIfRequired(final ClassLoader loader) {

    if (this.runner != null) {
      final CustomRunnerExecutor ce = new CustomRunnerExecutor(this.runner);
      Object foreignCe = ce;
      if (IsolationUtils.fromDifferentLoader(this.runner.getClass(), loader)) {
        foreignCe = IsolationUtils.cloneForLoader(ce, loader);
      }
      final Method run = Reflection.publicMethod(foreignCe.getClass(), "run");
      try {
        this.results = (Map<String, Throwable>) run.invoke(foreignCe);
      } catch (final Exception e) {
        throw translateCheckedException(e);
      }

      this.runner = null;
    }

  }

  private void readObject(final ObjectInputStream aInputStream)
      throws ClassNotFoundException, IOException {

    aInputStream.defaultReadObject();
    this.runner = createRunner(this.clazz);
    this.tus = new ArrayList<TestUnit>();
    gatherTestUnits(this.tus, this.runner.getDescription());

  }

  public Class<?> getClazz() {
    return this.clazz;
  }

  public Description getTestUnitDescriptionForString(final String description) {
    for (final TestUnit each : this.tus) {
      final RunnerAdapterTestUnit rutu = (RunnerAdapterTestUnit) each;
      if (CustomRunnerExecutor.descriptionToString(rutu.getJunitDescription())
          .equals(description)) {
        return rutu.getJunitDescription();
      }
    }
    return null;
  }

}
