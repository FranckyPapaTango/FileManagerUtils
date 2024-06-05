module com.rafaros.filemanagerutils {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires com.almasb.fxgl.all;
    requires java.desktop;


    requires org.apache.commons.imaging; // Ajoutez cette ligne

    opens com.rafaros.filemanagerutils to javafx.fxml;
    exports com.rafaros.filemanagerutils;


}