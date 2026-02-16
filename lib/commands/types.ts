/**
 * Location object containing geolocation coordinates and optional metadata.
 */
export interface Location {
  longitude: number | string;
  latitude: number | string;
  altitude?: number | string | null;
  /** Number of satellites being tracked (1-12). This value is ignored on real devices. */
  satellites?: number | string | null;
  /** Valid speed value. https://developer.android.com/reference/android/location/Location#setSpeed(float) */
  speed?: number | string | null;
  /** Valid bearing value. https://developer.android.com/reference/android/location/Location#setBearing(float) */
  bearing?: number | string | null;
  /** Valid accuracy value. https://developer.android.com/reference/android/location/Location#setAccuracy(float), https://developer.android.com/reference/android/location/Criteria. Should be greater than 0.0 meters/second for real devices or 0.0 knots for emulators. */
  accuracy?: number | string | null;
}

/**
 * Options for starting a media projection recording session.
 */
export interface StartMediaProjectionRecordingOpts {
  /** Maximum supported resolution on-device (Detected automatically by the app
   * itself), which usually equals to Full HD 1920x1080 on most phones however
   * you can change it to following supported resolutions as well: "1920x1080",
   * "1280x720", "720x480", "320x240", "176x144". */
  resolution?: string;
  /** Maximum allowed duration is 15 minutes; you can increase it if your test
   * takes longer than that. */
  maxDurationSec?: number;
  /** Recording thread priority.
   * If you face performance drops during testing with recording enabled, you
   * can reduce recording priority */
  priority?: 'high' | 'normal' | 'low';
  /** You can type recording video file name as you want, but recording currently
   * supports only "mp4" format so your filename must end with ".mp4". An
   * invalid file name will fail to start the recording. */
  filename?: string;
}

/**
 * Supported locale information.
 */
export interface SupportedLocale {
  language: string;
  country: string;
  script?: string;
}

/**
 * Options for retrieving SMS list.
 */
export interface SmsListOptions {
  /** The maximum count of recent messages to retrieve */
  max?: number;
}

/**
 * Individual SMS message item.
 */
export interface SmsListResultItem {
  id: string;
  address: string;
  person: string | null;
  date: string;
  read: string;
  status: string;
  type: string;
  subject: string | null;
  body: string;
  serviceCenter: string | null;
}

/**
 * SMS list result containing items and total count.
 */
export interface SmsListResult {
  items: SmsListResultItem[];
  total: number;
}
