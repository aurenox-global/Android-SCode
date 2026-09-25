package dev.aldi.sayuti.editor.injection;

import java.util.ArrayList;

import a.a.a.jq;
import com.ascode.android.utility.FileUtil;
import com.ascode.android.xml.XmlBuilder;

public class ManifestInjection {

    public ArrayList arr;
    public jq jq;
    public String path;
    public String replace;
    public String value;

    public ManifestInjection(jq jqVar, ArrayList arrayList) {
        jq = jqVar;
        arr = arrayList;
    }

    public void b(XmlBuilder nx, String str, String str2) {
        path = FileUtil.getExternalStorageDir() + "/.AndroidSCode/data/" + jq.sc_id + "/injection/manifest/" + str;
        if (FileUtil.isExistFile(path)) {
            FileUtil.readFile(path).isEmpty();
        }
    }
}