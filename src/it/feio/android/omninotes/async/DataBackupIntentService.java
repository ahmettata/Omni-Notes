package it.feio.android.omninotes.async;

import it.feio.android.omninotes.MainActivity;
import it.feio.android.omninotes.R;
import it.feio.android.omninotes.db.DbHelper;
import it.feio.android.omninotes.models.Attachment;
import it.feio.android.omninotes.utils.Constants;
import it.feio.android.omninotes.utils.StorageManager;

import java.io.File;
import java.util.ArrayList;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;

public class DataBackupIntentService extends IntentService {

	private SharedPreferences prefs;


	public DataBackupIntentService() {
		super("DataBackupIntentService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		// PowerManager pm = (PowerManager)
		// getSystemService(Context.POWER_SERVICE);
		// PowerManager.WakeLock wl =
		// pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Constants.TAG);
		// // Acquire the lock
		// wl.acquire();
		

		prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_MULTI_PROCESS);

		// If an alarm has been fired a notification must be generated
		if (Constants.ACTION_DATA_EXPORT.equals(intent.getAction())) {
			exportData(intent);
		} else if (Constants.ACTION_DATA_IMPORT.equals(intent.getAction())) {
			importData(intent);
		} else if (Constants.ACTION_DATA_DELETE.equals(intent.getAction())) {
			deleteData(intent);
		}

		// Release the lock
		// Log.d(Constants.TAG, "Releasing power lock, all done");
		// wl.release();

	}

	
	synchronized private void exportData(Intent intent) {
		
		// Gets backup folder
		String backupName = intent.getStringExtra(Constants.INTENT_BACKUP_NAME);
		File backupDir = StorageManager.getBackupDir(backupName);
		
		// Directory clean in case of previously used backup name
		StorageManager.delete(this, backupDir.getAbsolutePath());
		
		// Directory is re-created in case of previously used backup name (removed above)
		backupDir = StorageManager.getBackupDir(backupName);
		
		// Database backup
		exportDB(backupDir);
		
		// Attachments backup
		exportAttachments(backupDir);		
		
		// Settings
		if (intent.getBooleanExtra(Constants.INTENT_BACKUP_INCLUDE_SETTINGS, true));
			exportSettings(backupDir);	
		
		// Notification of operation ended
		String title = getString(R.string.data_export_completed);
		String text = backupDir.getPath();
		createNotification(intent, this, title, text);
	}

	synchronized private void importData(Intent intent) {
		
		// Gets backup folder
		String backupName = intent.getStringExtra(Constants.INTENT_BACKUP_NAME);
		File backupDir = StorageManager.getBackupDir(backupName);
		
		// Database backup
		importDB(backupDir);
		
		// Attachments backup
		importAttachments(backupDir);	
		
		// Settings restore
		importSettings(backupDir);
		
		String title = getString(R.string.data_import_completed);
		String text = getString(R.string.click_to_refresh_application);
		createNotification(intent, this, title, text);
	}

	
	synchronized private void deleteData(Intent intent) {
		
		// Gets backup folder
		String backupName = intent.getStringExtra(Constants.INTENT_BACKUP_NAME);
		File backupDir = StorageManager.getBackupDir(backupName);
		
		// Backup directory removal
		StorageManager.delete(this, backupDir.getAbsolutePath());
		
		String title = getString(R.string.data_deletion_completed);
		String text = backupName + " " + getString(R.string.deleted);
		createNotification(intent, this, title, text);
	}

	
	
	/**
	 * Creation of notification on operations completed
	 * @param intent2 
	 * @param ctx
	 * @param message
	 */
	private void createNotification(Intent intent, Context ctx, String title, String message) {

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(ctx)
				.setSmallIcon(R.drawable.ic_stat_notification_icon)
				.setContentTitle(title)
				.setContentText(message)
				.setAutoCancel(true);

		// Ringtone options
				String ringtone = prefs.getString("settings_notification_ringtone", null);
				if (ringtone != null) {
					mBuilder.setSound(Uri.parse(ringtone));
				}

		// Vibration options
		long[] pattern = {500,500};		
		if (prefs.getBoolean("settings_notification_vibration", true))
			mBuilder.setVibrate(pattern);

		// The behavior differs depending on intent action 
		Intent intentLaunch;
		if (Constants.ACTION_DATA_IMPORT.equals(intent.getAction())) {
			 intentLaunch = new Intent(ctx, MainActivity.class);
		} else {
			 intentLaunch = new Intent();
		}
			
		// Add this bundle to the intent
		intentLaunch.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); 
		intentLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//		intentLaunch.setAction(Constants.ACTION_START_APP);
		
		// Creates the PendingIntent
		PendingIntent notifyIntent = PendingIntent.getActivity(ctx, 0, intentLaunch, PendingIntent.FLAG_UPDATE_CURRENT);

		// Puts the PendingIntent into the notification builder
		mBuilder.setContentIntent(notifyIntent);
		// Notifications are issued by sending them to the
		// NotificationManager system service.
		NotificationManager mNotificationManager = (NotificationManager) ctx
				.getSystemService(Context.NOTIFICATION_SERVICE);
		// Builds an anonymous Notification object from the builder, and
		// passes it to the NotificationManager
		mNotificationManager.notify(0, mBuilder.build());
	}

	
	/**
	 * Export database to backup folder
	 * 
	 * @param backupDir
	 * @return True if success, false otherwise
	 */
	private boolean exportDB(File backupDir) {
		File database = getDatabasePath(Constants.DATABASE_NAME);
		return (StorageManager.copyFile(database, new File(backupDir, Constants.DATABASE_NAME)) );
	}

	
	/**
	 * Export attachments to backup folder
	 * 
	 * @param backupDir
	 * @return True if success, false otherwise
	 */
	private boolean exportAttachments(File backupDir) {
		File attachmentsDir = StorageManager.getAttachmentDir(this);
		File destinationattachmentsDir = new File(backupDir, attachmentsDir.getName());
		
		DbHelper db = DbHelper.getInstance(this);
		ArrayList<Attachment> list = db.getAllAttachments();
		
		for (Attachment attachment : list) {
			StorageManager.copyToBackupDir(destinationattachmentsDir, new File(attachment.getUri().getPath()));
		}
		return true;
	}

	
	/**
	 * Exports settings if required
	 * @param backupDir
	 * @return
	 */
	private boolean exportSettings(File backupDir) {
		File preferences = StorageManager.getSharedPreferencesFile(this);
		return (StorageManager.copyFile(preferences, new File(backupDir, preferences.getName())) );
	}

	
	/**
	 * Imports settings 
	 * @param backupDir
	 * @return
	 */
	private boolean importSettings(File backupDir) {
		File preferences = StorageManager.getSharedPreferencesFile(this);
		File preferenceBackup = new File(backupDir, preferences.getName());
		return (StorageManager.copyFile(preferenceBackup, preferences));
	}
	

	
	/**
	 * Import database from backup folder
	 * 
	 * @param backupDir
	 * @return True if success, false otherwise
	 */
	private boolean importDB(File backupDir) {
		File database = getDatabasePath(Constants.DATABASE_NAME);
		if (database.exists()) {
			database.delete();
		}
		return (StorageManager.copyFile(new File(backupDir, Constants.DATABASE_NAME), database));
	}

	
	/**
	 * Import attachments from backup folder
	 * 
	 * @param backupDir
	 * @return True if success, false otherwise
	 */
	private boolean importAttachments(File backupDir) {
		File attachmentsDir = StorageManager.getAttachmentDir(this);
		// Clearing
		StorageManager.delete(this, attachmentsDir.getAbsolutePath());
		// Moving back
		File backupAttachmentsDir = new File(backupDir, attachmentsDir.getName());
		return (StorageManager.copyDirectory(backupAttachmentsDir, attachmentsDir));
	}

}
