package de.blinkt.openvpn.remote;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Base64;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ConfigParser.ConfigParseError;

public class ParseUtil {
	private VpnProfile mResult; 
	private List<String> mPathsegments;
	private String mAliasName=null;
	private String mPossibleName=null;
	
	
	public VpnProfile config(Context context){
		Uri uri = new Uri.Builder().path(ConfigFileUtil.getConfigFile()).scheme("file").build();
		mPossibleName = uri.getLastPathSegment();
		if(mPossibleName!=null){
			mPossibleName =mPossibleName.replace(".ovpn", "");
			mPossibleName =mPossibleName.replace(".conf", "");
		}
		mPathsegments = uri.getPathSegments();
		try {
			InputStream is = context.getContentResolver().openInputStream(uri);
			return parseVpnProfile(is);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}
	

	public VpnProfile parseVpnProfile(InputStream is) {
		ConfigParser cp = new ConfigParser();
		try {
			InputStreamReader isr = new InputStreamReader(is);
			cp.parseConfig(isr);
			VpnProfile vp = cp.convertProfile();
			embedFiles(vp);
			return vp;

		} catch (IOException e) {
			e.printStackTrace();
		} catch (ConfigParseError e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private File findFileRaw(String filename) {
        if (filename == null || filename.equals(""))
            return null;

        // Try diffent path relative to /mnt/sdcard
        File sdcard = Environment.getExternalStorageDirectory();
        File root = new File("/");

        HashSet<File> dirlist = new HashSet<File>();

        for (int i = mPathsegments.size() - 1; i >= 0; i--) {
            String path = "";
            for (int j = 0; j <= i; j++) {
                path += "/" + mPathsegments.get(j);
            }
            // Do a little hackish dance for the Android File Importer
            // /document/primary:ovpn/openvpn-imt.conf


            if (path.indexOf(':') != -1 && path.lastIndexOf('/') > path.indexOf(':')) {
                String possibleDir = path.substring(path.indexOf(':') + 1, path.length());
                possibleDir = possibleDir.substring(0, possibleDir.lastIndexOf('/'));


                dirlist.add(new File(sdcard, possibleDir));

            }
            dirlist.add(new File(path));


        }
        dirlist.add(sdcard);
        dirlist.add(root);


        String[] fileparts = filename.split("/");
        for (File rootdir : dirlist) {
            String suffix = "";
            for (int i = fileparts.length - 1; i >= 0; i--) {
                if (i == fileparts.length - 1)
                    suffix = fileparts[i];
                else
                    suffix = fileparts[i] + "/" + suffix;

                File possibleFile = new File(rootdir, suffix);
                if (!possibleFile.canRead())
                    continue;

                // read the file inline
                return possibleFile;

            }
        }
        return null;
    }
	

	private void embedFiles(VpnProfile mResult) {
		  if (mResult.mPKCS12Filename != null) {
	            File pkcs12file = findFileRaw(mResult.mPKCS12Filename);
	            if (pkcs12file != null) {
	                mAliasName = pkcs12file.getName().replace(".p12", "");
	            } else {
	                mAliasName = "Imported PKCS12";
	            }
	        }
		mResult.mCaFilename = embedFile(mResult.mCaFilename);
		mResult.mClientCertFilename = embedFile(mResult.mClientCertFilename);
		mResult.mClientKeyFilename = embedFile(mResult.mClientKeyFilename);
		mResult.mTLSAuthFilename = embedFile(mResult.mTLSAuthFilename);
		mResult.mPKCS12Filename = embedFile(mResult.mPKCS12Filename, true);
		if (mResult.mUsername == null && mResult.mPassword != null) {
			String data = embedFile(mResult.mPassword);
			ConfigParser.useEmbbedUserAuth(mResult, data);
		}
	}

	private String embedFile(String filename) {
		return embedFile(filename, false);
	}


	private String embedFile(String filename, boolean base64encode) {
		if (filename == null)
			return null;

		if (filename.startsWith(VpnProfile.INLINE_TAG))
			return filename;

		File possibleFile = findFile(filename);
		if (possibleFile == null)
			return filename;
		else
			return readFileContent(possibleFile, base64encode);

	}

	private File findFile(String filename) {
		File foundfile = findFileRaw(filename);

		return foundfile;
	}

	String readFileContent(File possibleFile, boolean base64encode) {
		byte[] filedata;
		try {
			filedata = readBytesFromFile(possibleFile);
		} catch (IOException e) {
			return null;
		}

		String data;
		if (base64encode) {
			data = Base64.encodeToString(filedata, Base64.DEFAULT);
		} else {
			data = new String(filedata);

		}
		return VpnProfile.INLINE_TAG + data;

	}

	private byte[] readBytesFromFile(File file) throws IOException {
		InputStream input = new FileInputStream(file);

		long len = file.length();

		byte[] bytes = new byte[(int) len];

		int offset = 0;
		int bytesRead = 0;
		while (offset < bytes.length
				&& (bytesRead = input
						.read(bytes, offset, bytes.length - offset)) >= 0) {
			offset += bytesRead;
		}

		input.close();
		return bytes;
	}

}
