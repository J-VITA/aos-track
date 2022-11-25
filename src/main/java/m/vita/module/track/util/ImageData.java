package m.vita.module.track.util;

import java.util.ArrayList;
import java.util.List;

/* Custom Image data */
public class ImageData {
    private List<data> imageData;

    public List<data> getData() {
        if ( imageData == null ) imageData = new ArrayList<data>();
        return imageData;
    }

    public void setData(List<data> imageData) {
        this.imageData = imageData;
    }

    public static class data {
        /* getter, setter data */
        private String brand_nm;
        private String uriFileName;
        private String editPop;
        private String resStr;

        public String getBrand_nm() {
            return brand_nm;
        }

        public void setBrand_nm(String brand_nm) {
            this.brand_nm = brand_nm;
        }

        public String getUriFileName() {
            return uriFileName;
        }

        public void setUriFileName(String uriFileName) {
            this.uriFileName = uriFileName;
        }

        public String getEditPop() {
            return editPop;
        }

        public void setEditPop(String editPop) {
            this.editPop = editPop;
        }

        public String getResStr() {
            return resStr;
        }

        public void setResStr(String resStr) {
            this.resStr = resStr;
        }
    }
}
