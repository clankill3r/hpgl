/*
mvn package -DskipTests

*/


package nl.doekewartena.hpgl;

import processing.core.PApplet;
import static processing.core.PApplet.*;
import processing.core.PGraphics;
import processing.serial.*;

public class HPGLPlotter extends PGraphics {
    
    public final static String esc = (char)27+"."; // for hpgl escape commands

    public PApplet papplet;
    public boolean goto_00_after_plot = true;
    public Serial serial;
    public String in_buffer = "";
    public int buffer_size;

    //int xonxoff = 1;
    //int rtscts = 0;

    // window
    public int x1;
    public int y1;
    public int x2;
    public int y2;

    public int max_plot_w_mm = -1;
    public int max_plot_h_mm = -1;

    public int plot_w_mm = -1;
    public int plot_h_mm = -1;

    public float align_x = 0.5f; // 0. to 1.
    public float align_y = 0.5f; // 0. to 1.
    

    public HPGLPlotter(PApplet papplet) {
        this(papplet, null);
    }

    /**
     * default to baudrate 9600, byte_size 8, parity 'N', stop_bits 1
     */
    public HPGLPlotter(PApplet papplet, String port) {
        this(papplet, port, 9600, 8, 'N', 1);
    }

    public HPGLPlotter(PApplet papplet, String port, int baudrate, int byte_size, char parity, int stop_bits) {
        this.papplet = papplet;
        init(port, baudrate, byte_size, parity, stop_bits);
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - 

    public void init(String port, int baudrate, int byte_size, char parity, int stop_bits) {

        // We try to figure out the port, this is not tested on many devices / OSs.
        if (port == null) {

            String[] serial_ports = Serial.list();
            for (int i = 0; i < serial_ports.length; i++) {
                
                if (platform == WINDOWS) {
                    // TODO (which COM, and is it always the same?)
                }
                else if (platform == MACOSX || platform == LINUX) {
                    if (serial_ports[i].contains("tty.usbserial")) {
                        port = serial_ports[i];
                    }
                }
            }
        }

        // TODO check for things going wrong, and set something like did_init_correct

        serial = new Serial(papplet, port, baudrate, parity, byte_size, stop_bits);

        command("IN");

        String indification = output_command("OI");
        println("plotter: "+indification);

        String buffer_size_str = output_command(esc+"L");
        println("buffer_size: "+buffer_size_str);
        buffer_size = Integer.parseInt(buffer_size_str) / 2; // Use half buffer for now...

        String window_str = output_command("OW");
        String[] tokens = split(window_str, ",");
        x1 = Integer.parseInt(tokens[0]);
        y1 = Integer.parseInt(tokens[1]);
        x2 = Integer.parseInt(tokens[2]);
        y2 = Integer.parseInt(tokens[3]);
        println("window: "+window_str);

        command("SP1");
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - -

    public void endRecord() {
        println("endRecord");
        plot();
        String error = output_command("OE");
        println("error: "+error);
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - 

    void command(String s) {    
        if (!s.endsWith(";")) {
            s += ";";
        }
        in_buffer += s;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - 

    String output_command(String s) {

        if (!is_output_command(s)) {
            println("[Error] command <"+s+"> is not an output command");
            return null;
        }

        String result = "";

        //serial.clear();
        serial.write(s);

        //result = null;
        //while (result == null) {
        //  result = serial.readStringUntil('\n');
        //  delay(1);
        //}
        //result = serial.readStringUntil('\n'); // either null or it hangs...

        boolean carriage_return_found = false;

        while (!carriage_return_found) {

            while (serial.available() > 0) {
                int in_byte = serial.read();
                if (in_byte == 13) { // carriage return
                    carriage_return_found = true;
                    break;
                }
                result += (char)in_byte;
            }
            // without this it hangs... (maybe delay in micro seconds?)
            papplet.delay(1);
        }

        return result;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - 

    boolean is_output_command(String c) {

        c = c.replace(";", "");

        switch (c) {
            case "OA":       case "OC":       case "OD":       case "OE":       case "OF":   
            case "OG":       case "OH":       case "OI":       case "OK":       case "OL":   
            case "OO":       case "OP":       case "OS":       case "OT":       case "OW":   
            case esc+"@":    case esc+"A":    case esc+"B":    case esc+"E":    case esc+"H":   
            case esc+"I":    case esc+"J":    case esc+"K":    case esc+"L":    case esc+"M":   
            case esc+"N":    case esc+"O":    case esc+"P":    case esc+"Q":    case esc+"R":   
            case esc+"S":    case esc+"T":    case esc+"U":    case esc+"V":    case esc+"(":   
            case esc+"Z":    case esc+")":
                return true;
            default:
                return false;
        }
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - -

    public void endDraw() {
        plot();
    }

    void plot() {

        // we assume every char in the in_buffer represents 1 byte
        // this is not the case for things like \n \t \r etc.
        // but since it will never end up larger then we estimate
        // this won't be a problem and avoids a more expensive getBytes("UTF-8").length check.

        println(in_buffer);

        // Allow a better view when it's done
        if (goto_00_after_plot) {
            command("PU");
            command("PA0,0");
        }

        while (!in_buffer.equals("")) {
            if (in_buffer.length() > buffer_size) {
                String buffer_piece = in_buffer.substring(0, buffer_size);
                int last_index_of_semicolon = buffer_piece.lastIndexOf(";");
                buffer_piece = in_buffer.substring(0, last_index_of_semicolon+1);
                in_buffer = in_buffer.substring(last_index_of_semicolon+1, in_buffer.length());
                serial.write(buffer_piece);
                println(">>"+buffer_piece);
            } else {
                serial.write(in_buffer);  
                println(">>"+in_buffer);
                in_buffer = "";
            }

            // we make a output call so we know the buffer is cleared in the plotter
            // @TODO SLOOOOOOWWWWWW........
            //String os = output_command("OS"); println("OS: "+os);
            String oe = output_command("OE"); 
            println("OE: "+oe);
        }
    }
    // - - - - - - - - - - - - - - - - - - - - - - - - - -

    public float screenX(float x, float y) {
        return papplet.screenX(x, y);
    }

    public float screenY(float x, float y) {
        return papplet.screenY(x, y);
    }

    public float screenX(float x, float y, float z) {
        return papplet.screenX(x, y, z);
    }

    public float screenY(float x, float y, float z) {
        return papplet.screenY(x, y, z);
    }

    public float screenZ(float x, float y, float z) {
        return papplet.screenZ(x, y, z);
    }

    public void line(float x1, float y1, float x2, float y2) {
        float _x1 = screenX(x1, y1);
        float _y1 = screenY(x1, y1);
        float _x2 = screenX(x2, y2);
        float _y2 = screenY(x2, y2);
        command("PU");
        command("PA"+x(_x1)+","+y(_y1));
        command("PD");
        command("PA"+x(_x2)+","+y(_y2));
    }

    public void line(float x1, float y1, float z1, float x2, float y2, float z2) {
        float _x1 = screenX(x1, y1, z1);
        float _y1 = screenY(x1, y1, z1);
        float _x2 = screenX(x2, y2, z2);
        float _y2 = screenY(x2, y2, z2);
        command("PU");
        command("PA"+x(_x1)+","+y(_y1));
        command("PD");
        command("PA"+x(_x2)+","+y(_y2));
    }


    // We assume that beginContour
    // is used after some part or the Exterior shape is defined

    // @TODO adjust and test with a plotter that supports edge / fill / contour
    public void endShape(int mode) {

        if (papplet.g.fill || papplet.g.stroke) {

            if (mode == CLOSE) {
                float[] v = vertices[0];
                vertex(v[X], v[Y], v[Z]);
            }

            switch(shape) {

                case POLYGON: 
                {

                    command("PU");
                    float[] v0 = vertices[0];
                    command("PA"+x(v0[X], v0[Y], v0[Z])+","+y(v0[X], v0[Y], v0[Z]));
                    command("PM0");
                    command("PD");
                    for (int i = 1; i < vertexCount; i++) {
                        float[] v = vertices[i];

                        if (Float.isNaN(v[X]) && Float.isNaN(v[Y]) && Float.isNaN(v[Z])) {
                            command("PM1");
                        } else {
                            command("PA"+x(v[X], v[Y], v[Z])+","+y(v[X], v[Y], v[Z]));
                        }
                    }
                    command("PM2");
                    break;
                }

                case POINTS: 
                {
                    for (int i = 0; i < vertexCount; i++) {
                        float[] v = vertices[i];
                        command("PU;PA"+x(v[X], v[Y], v[Z])+","+y(v[X], v[Y], v[Z])+";PD");
                    }
                    break;
                }

                case LINES: 
                {
                    for (int i = 0; i < vertexCount-1; i += 2) {
                        float[] v1 = vertices[i];
                        float[] v2 = vertices[i+1];
                        command("PU");
                        command("PA"+x(v1[X], v1[Y], v1[Z])+","+y(v1[X], v1[Y], v1[Z]));
                        command("PD");
                        command("PA"+x(v2[X], v2[Y], v2[Z])+","+y(v2[X], v2[Y], v2[Z]));
                    }
                    break;
                }

                case TRIANGLES: 
                {

                    for (int i = 0; i < vertexCount-2; i += 3) {

                        float[] v1 = vertices[i];
                        float[] v2 = vertices[i+1];
                        float[] v3 = vertices[i+2];

                        command("PU");
                        command("PA"+x(v1[X], v1[Y], v1[Z])+","+y(v1[X], v1[Y], v1[Z]));
                        command("PM0");
                        command("PD");
                        command("PA"+x(v2[X], v2[Y], v2[Z])+","+y(v2[X], v2[Y], v2[Z]));
                        command("PA"+x(v3[X], v3[Y], v3[Z])+","+y(v3[X], v3[Y], v3[Z]));
                        command("PA"+x(v1[X], v1[Y], v1[Z])+","+y(v1[X], v1[Y], v1[Z]));
                        command("PM2"); 

                    }
                    break;
                }

                case TRIANGLE_STRIP: 
                {

                    for (int i = 0; i < vertexCount-2; i += 1) {
                        float[] v1 = vertices[i];
                        float[] v2 = vertices[i+1];
                        float[] v3 = vertices[i+2];
                        command("PU");
                        command("PA"+x(v1[X], v1[Y], v1[Z])+","+y(v1[X], v1[Y], v1[Z]));
                        command("PM0");
                        command("PD");
                        command("PA"+x(v2[X], v2[Y], v2[Z])+","+y(v2[X], v2[Y], v2[Z]));
                        command("PA"+x(v3[X], v3[Y], v3[Z])+","+y(v3[X], v3[Y], v3[Z]));
                        command("PA"+x(v1[X], v1[Y], v1[Z])+","+y(v1[X], v1[Y], v1[Z]));
                        command("PM2");
                    }
                    break;
                }

                case TRIANGLE_FAN: 
                {

                    float[] v0 = vertices[0];
                    for (int i = 0; i < vertexCount-1; i += 1) {
                        float[] v1 = vertices[i];
                        float[] v2 = vertices[i+1];
                        command("PU");
                        command("PA"+x(v1[X], v1[Y], v1[Z])+","+y(v1[X], v1[Y], v1[Z]));
                        command("PM0");
                        command("PD");
                        command("PA"+x(v2[X], v2[Y], v2[Z])+","+y(v2[X], v2[Y], v2[Z]));
                        command("PA"+x(v0[X], v0[Y], v0[Z])+","+y(v0[X], v0[Y], v0[Z]));
                        command("PM2");
                    }
                    break;
                }

                case QUADS: 
                {

                    for (int i = 0; i < vertexCount-3; i += 4) {
                        float[] v1 = vertices[i];
                        float[] v2 = vertices[i+1];
                        float[] v3 = vertices[i+2];
                        float[] v4 = vertices[i+3];

                        command("PU");
                        command("PA"+x(v1[X], v1[Y], v1[Z])+","+y(v1[X], v1[Y], v1[Z]));
                        command("PM0");
                        command("PD");
                        command("PA"+x(v2[X], v2[Y], v2[Z])+","+y(v2[X], v2[Y], v2[Z]));
                        command("PA"+x(v3[X], v3[Y], v3[Z])+","+y(v3[X], v3[Y], v3[Z]));
                        command("PA"+x(v4[X], v4[Y], v4[Z])+","+y(v4[X], v4[Y], v4[Z]));
                        command("PA"+x(v1[X], v1[Y], v1[Z])+","+y(v1[X], v1[Y], v1[Z]));
                        command("PM2");
                    }
                    break;
                }

                case QUAD_STRIP: 
                {
                    if (vertexCount >= 4) {
                        float[] pre_v1 = vertices[0];
                        float[] pre_v2 = vertices[1];
                        command("PU");
                        command("PA"+x(pre_v1[X], pre_v1[Y], pre_v1[Z])+","+y(pre_v1[X], pre_v1[Y], pre_v1[Z]));
                        command("PD");
                        command("PA"+x(pre_v2[X], pre_v2[Y], pre_v2[Z])+","+y(pre_v2[X], pre_v2[Y], pre_v2[Z]));
                        for (int i = 2; i < vertexCount-1; i += 2) {
                            float[] v1 = vertices[i];
                            float[] v2 = vertices[i+1];
                            command("PU");
                            command("PA"+x(pre_v1[X], pre_v1[Y], pre_v1[Z])+","+y(pre_v1[X], pre_v1[Y], pre_v1[Z]));
                            command("PD");
                            command("PA"+x(v1[X], v1[Y], v1[Z])+","+y(v1[X], v1[Y], v1[Z]));
                            command("PA"+x(v2[X], v2[Y], v2[Z])+","+y(v2[X], v2[Y], v2[Z]));
                            command("PA"+x(pre_v2[X], pre_v2[Y], pre_v2[Z])+","+y(pre_v2[X], pre_v2[Y], pre_v2[Z]));
                            pre_v1 = v1;
                            pre_v2 = v2;
                        }
                    }
                    break;
                }
            }
        }

        // reset
        vertexCount = 0;
        curveVertexCount = 0;
    }



    // CORNER
    public void ellipseImpl(float x, float y, float w, float h) {

        beginShape();
        int steps = 100; // TODO
        float angle = 0;
        float angle_inc = TWO_PI / steps;
        for (int i = 0; i < steps; i++) {
            float _x = x + cos(angle) * w / 2;
            float _y = y + sin(angle) * h / 2;
            vertex(_x, _y);
            angle += angle_inc;
        }
        endShape();
    }



    // CORNER
    public void arcImpl(float x, float y, float w, float h, float start, float stop, int mode) {

        //papplet.g.fill(255,0,0);             
        //papplet.g.ellipse(x, y, 10, 10);
        //papplet.g.noFill();

        beginShape();

        int steps = 100; // TODO

        for (int i = 0; i < steps; i++) {
            float t = (float) i / (steps-1);
            float a = lerp(start, stop, t);
            float px = x + cos(a) * w;
            float py = y + sin(a) * h;
            vertex(px, py);
        }

        if (mode == PIE) {
            vertex(x, y);
            float[] v0 = vertices[0];
            vertex(v0[X], v0[Y]);
        }
        if (mode == CHORD) {
            float[] v0 = vertices[0];
            vertex(v0[X], v0[Y]);
        }
        endShape();
    }


    // ---------------
    float getFrameScaleValue(float containerW, float containerH, float contentW, float contentH) {
        float containerProportion = containerW / containerH;
        float contentProportion = contentW / contentH;

        if (contentProportion >= containerProportion) { //container landscape (or square)
            return containerW / contentW;
        } else {
            return containerH / contentH;
        }
    }
    // ------------


    int x(float x) {

        //float sketch_ratio = (float) papplet.width / papplet.height;
        //float plot_ratio = (float) p.plot_w_mm / p.plot_h_mm;

        float scale;

        if ((float) papplet.width / papplet.height >= (float) plot_w_mm / plot_h_mm) {
            scale = (float) plot_w_mm / papplet.width;
        }
        else {
            scale = (float) plot_h_mm / papplet.height;
        }

        //println(scale);

        int plot_steps_x = (int) (norm(plot_w_mm, 0, max_plot_w_mm) * (x2 - x1));
        int remaining_plot_steps_x = (x2 - x1) - plot_steps_x;
        int offset_x = (int) (align_x * remaining_plot_steps_x);
        return (int) map(x, papplet.width, 0, 0, plot_steps_x) + offset_x;
    }

    int y(float y) {
        int plot_steps_y = (int) (norm(plot_h_mm, 0, max_plot_h_mm) * (y2 - y1));
        int remaining_plot_steps_y = (y2 - y1) - plot_steps_y;
        int offset_y = (int) (align_y * remaining_plot_steps_y);
        return (int) map(y, papplet.height, 0, 0, plot_steps_y) + offset_y;
    }

    int x(float x, float y, float z) {
        float _x = screenX(x, y, z);
        return x(_x);
    }

    int y(float x, float y, float z) {
        float _y = screenY(x, y, z);
        return y(_y);
    }

    public void pushMatrix() {
    }
    public void popMatrix() {
    }
    public void translate(float x, float y) {
    }
    public void translate(float x, float y, float z) {
    }
    public void scale(float s) {
    }
    public void scale(float x, float y) {
    }
    public void scale(float x, float y, float z) {
    }
    public void rotate(float a) {
    }
    public void rotateX(float a) {
    }
    public void rotateY(float a) {
    }
    public void rotateZ(float a) {
    }
    public void shearX(float a) {
    }
    public void shearY(float a) {
    }
    public void text(String s, float x, float y) {
    }
    public void stroke(float grey) {
    }
    public void stroke(int rgb) {
    }
    public void stroke(float gray, float alpha) {
    }
    public void stroke(float v1, float v2, float v3) {
    }
    public void stroke(float v1, float v2, float v3, float alpha) {
    }
    public void fill(float grey) {
    }
    public void fill(int rgb) {
    }
    public void fill(float gray, float alpha) {
    }
    public void fill(float v1, float v2, float v3) {
    }
    public void fill(float v1, float v2, float v3, float alpha) {
    }

    public void resetMatrix() {
    }

    public void beginContour() {
        // @Hack
        // When we process endShape we need to know when
        // beginContour / endContour was called
        // we compare against the NaN and assume it was us...
        vertex(Float.NaN, Float.NaN, Float.NaN);
    }

    public void endContour() {
        // @Hack, see beginContour
        vertex(Float.NaN, Float.NaN, Float.NaN);
    }

}
