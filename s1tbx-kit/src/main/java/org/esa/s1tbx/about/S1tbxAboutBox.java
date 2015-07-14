/*
 * Copyright (C) 2015 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.s1tbx.about;

import org.esa.snap.rcp.about.AboutBox;

import javax.swing.*;

/**
 * @author Norman
 */
@AboutBox(displayName = "S1TBX", position = 10)
public class S1tbxAboutBox extends JLabel {
    
    public S1tbxAboutBox() {
        super("<html>" +
                      "<b>S1TBX</b>" +
                      "<br>(c) Copyright 2015 by Array Systems Computing Inc. and contributors. All rights reserved." +
                      "<br>" +
                      "<b>JLinda</b>" +
                      "<br>(c) Copyright 2009-2014 by PPO.labs and contributors. All rights reserved." +
                      "<br>" +
                      "<br>This program has been developed under contract to ESA (ESRIN)." +
                      "<br><br>" +
                      "<u><b>The S1TBX team</b></u>" +
                      "<br>" +
                      "<table border=0>" +
                      "<tr><td>" +
                      "<b>Array Systems Computing</b>:" +
                      "</td></tr>" +
                      "&nbsp;&nbsp;<b>Rajesh Jha</b><br>" +
                      "&nbsp;&nbsp;<b>Luis Veci</b><br>" +
                      "</td><td>" +
                      "&nbsp;&nbsp;<b>Jun Lu</b><br>" +
                      "&nbsp;&nbsp;<b>Cecilia Wong</b> <br>" +
                      "</td><td>" +
                      "&nbsp;&nbsp;<b>Serge Stankovic</b><br>" +
                      "&nbsp;&nbsp;<b>Andrew Taylor</b><br>" +
                      "</td></tr>" +
                      "</table>" +
                      "<table border=0>" +
                      "<tr><td>" +
                      "<b>German Aerospace Center</b> (DLR):" +
                      "</td><td>" +
                      "&nbsp;&nbsp;<b>Pau Prats-Iraola</b><br>" +
                      "</td><td>" +
                      "&nbsp;&nbsp;<b>Rolf Scheiber</b><br>" +
                      "</td><td>" +
                      "&nbsp;&nbsp;<b>Marc Rodriguez-Cassola</b><br>" +
                      "</td></tr>" +
                      "</table>" +
                      "<table border=0>" +
                      "<tr><td>" +
                      "<b>Ocean Data Lab</b> (ODL):" +
                      "</td><td>" +
                      "&nbsp;&nbsp;<b>Fabrice Collard</b><br>" +
                      "</td></tr>" +
                      "</table>" +
                      "<table border=0>" +
                      "<tr><td>" +
                      "<b>European Space Agency</b> (ESA) ESRIN:<br>" +
                      "</td><td>" +
                      "&nbsp;&nbsp;<b>Marcus Engdahl</b><br>" +
                      "</td><td>" +
                      "&nbsp;&nbsp;<b>Michael Foumelis</b><br>" +
                      "</td></tr>" +
                      "</table>" +
                      "<br>This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License" +
                      "<br>as published by the Free Software Foundation. This program is distributed in the hope it will be useful, but WITHOUT ANY" +
                      "<br>WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE." +
                      "<br>See the GNU General Public License for more details." +
                      "</html>"
        );
    }
}
