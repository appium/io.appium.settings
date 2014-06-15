package io.appium.settings;

import android.content.Context;


public abstract class Service {
  protected Context context;

  public Service(Context context) {
    this.context = context;
  }

  public abstract boolean enable();
  public abstract boolean disable();
}
