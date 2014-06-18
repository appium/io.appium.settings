package io.appium.settings;

import android.content.Context;


public abstract class Service {
  protected Context mContext;

  public Service(Context context) {
    this.mContext = context;
  }

  public abstract boolean enable();
  public abstract boolean disable();
}
