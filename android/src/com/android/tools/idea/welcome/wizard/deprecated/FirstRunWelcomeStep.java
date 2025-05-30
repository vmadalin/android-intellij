/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard.deprecated;

import com.android.tools.idea.welcome.wizard.FirstRunWizardTracker;
import com.google.wireless.android.sdk.stats.SetupWizardEvent;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.jetbrains.annotations.NotNull;

/**
 * Welcome page for the first run wizard
 *
 * @deprecated use {@link com.android.tools.idea.welcome.wizard.FirstRunWelcomeStep}
 */
@Deprecated
public final class FirstRunWelcomeStep extends FirstRunWizardStep {
  private final FirstRunWelcomeStepForm myForm;

  public FirstRunWelcomeStep(boolean sdkExists, @NotNull FirstRunWizardTracker tracker) {
    super("Welcome", "Android Studio", tracker);
    myForm = new FirstRunWelcomeStepForm(sdkExists);
    setComponent(myForm.getRoot());
  }

  @Override
  public void init() {
    // Nothing to init
  }

  @NotNull
  @Override
  public JLabel getMessageLabel() {
    throw new IllegalStateException();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    // Doesn't matter
    return myForm.getRoot();
  }

  @Override
  protected SetupWizardEvent.WizardStep.WizardStepKind getWizardStepKind() {
    return SetupWizardEvent.WizardStep.WizardStepKind.WELCOME;
  }
}
