import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import javafx.scene.control.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.fazecast.jSerialComm.SerialPort.TIMEOUT_WRITE_BLOCKING;

public class Engine {
    public ComboBox<String> coms,formatTextToSend;
    public Button con, disc, clearRx, send, Reset, asc, hex;
    private SerialPort actualPort;
    public TextField textsend;
    private int i = 0, dataIndex = 0, len = 0, len2 = 0, modByte, fcsMod, fec, modCC,index=0;
    private final byte[] reset = new byte[]{0x02, 0x00, 0x3C, 0x3C, 0x00};
    private final byte[] phy = new byte[]{0x02, 0x02, 0x08, 0x00, 0x10, 0x1A, 0x00};
    private final byte[] dl = new byte[]{0x02, 0x02, 0x08, 0x00, 0x11, 0x1B, 0x00};
    public TextArea area;
    public RadioButton dlb, phyb, bpskB, qpskB, epskB;
    public CheckBox fcb;
    private boolean start = false, sendack = false,hexBytes=true,boolSecond=false;
    private StringBuilder receivedTextHEX = new StringBuilder();
    private StringBuilder receivedTextASC = new StringBuilder();
    private StringBuilder dataText = new StringBuilder();
    private byte[] dataBytes;
    private static byte ack=0, nak=0, fcs2Byte = 0, fcs1Byte=0, commandByte=0;
    char firstByte=0,secondByte=0;
    private Status status = Status.Begin;
    private FormatStatus formatStatus = FormatStatus.HEX;

    public void initialize() {
        area.setDisable(true);
        disc.setDisable(true);
        send.setDisable(true);
        phyb.setDisable(true);
        dlb.setDisable(true);
        bpskB.setDisable(true);
        qpskB.setDisable(true);
        epskB.setDisable(true);
        area.setDisable(true);
        fcb.setDisable(true);
        Reset.setDisable(true);
        List<String> list = Arrays.stream(SerialPort.getCommPorts()).map(SerialPort::getSystemPortName).collect(Collectors.toList());
        coms.getItems().addAll(list);
        formatTextToSend.getItems().addAll("ASCII","HEX");
        formatTextToSend.getSelectionModel().select("ASCII");
        con.setOnMouseClicked(mouseEvent -> {
            try {
                actualPort = SerialPort.getCommPort(coms.getSelectionModel().getSelectedItem());
                actualPort.setComPortParameters(57600, 8, 1, 0);
                actualPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING | TIMEOUT_WRITE_BLOCKING, 0, 0);
                actualPort.clearRTS();
                actualPort.openPort();
                con.setDisable(true);
                coms.setDisable(true);
                fcb.setDisable(false);
                disc.setDisable(false);
                send.setDisable(false);
                area.setDisable(false);
                bpskB.setDisable(false);
                qpskB.setDisable(false);
                epskB.setDisable(false);
                dlb.setDisable(true);
                phyb.setDisable(false);
                area.setDisable(false);
                Reset.setDisable(false);


                if (actualPort.isOpen()) {
                    System.out.println("Connected");
                    dlb.setSelected(true);
                    phyb.setDisable(false);
                    actualPort.addDataListener(new SerialPortDataListener() {
                        @Override
                        public int getListeningEvents() {
                            return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
                        }

                        @Override
                        public void serialEvent(SerialPortEvent serialPortEvent) {

                            if (serialPortEvent.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE)
                                return;
                            i = 0;
                            System.out.println("Event detected");
                            //  actualPort.setComPortTimeouts(SerialPort.TIMEOUT_SCANNER, 0, 0);
                            InputStream input = actualPort.getInputStream();
                            try {
                                byte[] actualbyte = new byte[1];
                                while (input.available() > 0) {
                                    int numBytes = input.read(actualbyte, 0, 1);
                                    receivedTextHEX.append(String.format("%02x", actualbyte[0]));
                                    receivedTextASC.append((char) actualbyte[0]);
                                    switch (status) {
                                        case Begin:
                                            if (actualbyte[0] == 0x02) {
                                                sendack = true;
                                                status = Status.Len;
                                                System.out.println(String.format("begin: %02x ", actualbyte[0]));
                                            }
                                            if (actualbyte[0] == 0x03) {
                                                status = Status.Len;
                                                System.out.println(String.format("begin: %02x ", actualbyte[0]));
                                            } else if (actualbyte[0] == 0x06) {
                                                ack = actualbyte[0];
                                                System.out.println(String.format("ACK: %02x ", actualbyte[0]));
                                            } else if (actualbyte[0] == 0x15) {
                                                nak = actualbyte[0];
                                                System.out.println(String.format("NAK: %02x ", actualbyte[0]));
                                            }
                                            break;
                                        case Len:
                                            len = actualbyte[0];
                                            len2 = len;
                                            System.out.println(String.format("len: %02x ", len));
                                            dataBytes = new byte[len];
                                            status = Status.Command;
                                            break;
                                        case Command:
                                            commandByte = actualbyte[0];
                                            System.out.println(String.format("command: %02x ", commandByte));
                                            status = Status.Data;
                                            if (len == 0) {
                                                status = Status.FCS_1;
                                            }
                                            break;

                                        case Data:
                                            System.out.println("data");
                                            dataBytes[dataIndex++] = actualbyte[0];
                                            dataText.append(String.format("%02x", actualbyte[0]));
                                            len--;
                                            if (len == 0) {
                                                System.out.println("dataend");
                                                status = Status.FCS_1;
                                                dataIndex = 0;
                                                break;
                                            }

                                            break;
                                        case FCS_1:
                                            fcs1Byte = actualbyte[0];
                                            System.out.println(String.format("fcs1: %02x ", fcs1Byte));
                                            status = Status.FCS_2;
                                            break;

                                        case FCS_2:
                                            fcs2Byte = actualbyte[0];
                                            System.out.println(String.format("fcs2: %02x ", fcs2Byte));
                                            checkFCS(fcs1Byte, fcs2Byte, len2, commandByte, dataBytes);
                                            if (sendack) {
                                                sendACK(new byte[]{0x06});
                                                sendack = false;
                                            }
                                            System.out.print("Data: " + dataText);
                                            System.out.println(String.format(", Command: %02x, FCS_1 : %02x, FCS_2: %02x", commandByte, fcs1Byte, fcs2Byte));
                                            dataText.delete(0, dataText.length());
                                            status = Status.Begin;
                                            break;
                                    }

                                }
                                start = false;
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            area.setVisible(true);
                            receivedTextHEX.append("\n");
                            receivedTextASC.append("\n");
                            if (formatStatus == FormatStatus.HEX)
                                area.setText(receivedTextHEX.toString().trim());
                            else if (formatStatus == FormatStatus.ASCII)
                                area.setText(receivedTextASC.toString().trim());
                        }
                    });


                }
                send.setOnMouseClicked(mouseEvent1 -> {
                    if(formatTextToSend.getSelectionModel().getSelectedItem()=="HEX") {
                        int size = textsend.getText().length() / 2;
                        byte[] bytesToSend = new byte[size];
                        for (int i = 0; i < textsend.getText().length(); i++) {
                            switch (textsend.getText().charAt(i)) {

                                default:
                                    if (hexBytes) {
                                        firstByte = textsend.getText().charAt(i);
                                        System.out.println("mam pierwszego bajta " + firstByte);
                                        hexBytes = false;
                                        boolSecond = true;
                                        break;

                                    }
                                    if (boolSecond) {
                                        secondByte = textsend.getText().charAt(i);
                                        StringBuilder buildByte = new StringBuilder();
                                        buildByte.append(firstByte);
                                        buildByte.append(secondByte);
                                        try
                                        {
                                            Byte byteToSend = (byte) Integer.parseInt(buildByte.toString(), 16);
                                            System.out.println(String.format("%02x", byteToSend));
                                            bytesToSend[index] = byteToSend;
                                            index++;
                                            hexBytes = true;
                                            boolSecond = false;
                                            break;
                                        }
                                        catch(NumberFormatException e){
                                            System.out.println("Niepoprawną wartość");
                                            break;
                                        }

                                    }
                                    break;
                            }

                        }

                        byte[] toSend = packToFrame(bytesToSend);
                        index = 0;
                        sending(toSend);
                    }
                    else{
                        byte[] toSend = packToFrame(textsend.getText().getBytes());
                        sending(toSend);
                    }

                });
                Reset.setOnMouseClicked(mouseEvent12 -> {
                    actualPort.setRTS();
                    actualPort.setDTR();
                    System.out.println("reset...");
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    actualPort.writeBytes(reset, reset.length);

                    actualPort.clearRTS();
                    actualPort.clearDTR();

                });
                hex.setOnMouseClicked(mouseEvent13 -> {
                    formatStatus = FormatStatus.HEX;
                    area.setText(receivedTextHEX.toString().trim());
                });
                asc.setOnMouseClicked(mouseEvent14 -> {
                    formatStatus = FormatStatus.ASCII;
                    area.setText(receivedTextASC.toString().trim());
                });
                phyb.setOnAction(actionEvent -> {
                    dlb.setSelected(false);
                    dlb.setDisable(false);
                    phyb.setDisable(true);
                    sending(phy);
                    System.out.println("Tryb PHY włączony");
                });
                dlb.setOnAction(actionEvent -> {
                    phyb.setSelected(false);
                    dlb.setDisable(true);
                    phyb.setDisable(false);
                    sending(dl);
                    System.out.println("Tryb DL włączony");
                });
                fcb.setOnAction(actionEvent ->
                {
                    if (fec == 0) {
                        modByte+=64;
                        fec=1;
                        System.out.println("F checked");
                    }
                    else if (fec == 1) {
                        modByte-=64;
                        fec=0;
                        System.out.println("F unchecked");
                    }
                });
                bpskB.setOnAction(actionEvent -> {
                    modByte=0;
                    fec=0;
                    fcb.setSelected(false);
                    qpskB.setSelected(false);
                    epskB.setSelected(false);
                    bpskB.setDisable(true);
                    qpskB.setDisable(false);
                    epskB.setDisable(false);
                    modByte += 4;
                    System.out.println("Modulacja B-PSK włączona");
                });
                qpskB.setOnAction(actionEvent -> {
                    modByte=0;
                    fec=0;
                    fcb.setSelected(false);
                    bpskB.setSelected(false);
                    epskB.setSelected(false);
                    bpskB.setDisable(false);
                    qpskB.setDisable(true);
                    epskB.setDisable(false);
                    modByte += 20;
                    System.out.println("Modulacja Q-PSK włączona");
                });
                epskB.setOnAction(actionEvent -> {
                    modByte=0;
                    fec=0;
                    fcb.setSelected(false);
                    qpskB.setSelected(false);
                    bpskB.setSelected(false);
                    bpskB.setDisable(false);
                    qpskB.setDisable(false);
                    epskB.setDisable(true);
                    modByte += 36;
                    System.out.println("Modulacja 8-PSK włączona");

                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });


        disc.setOnMouseClicked(mouseEvent -> {
            try {
                actualPort.closePort();
                con.setDisable(false);
                disc.setDisable(true);
                coms.setDisable(false);
                send.setDisable(true);
                phyb.setDisable(true);
                dlb.setDisable(true);
                fcb.setDisable(true);
                bpskB.setDisable(true);
                qpskB.setDisable(true);
                epskB.setDisable(true);
                area.setDisable(true);
                Reset.setDisable(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        clearRx.setOnMouseClicked(mouseEvent -> {
            area.clear();
            receivedTextHEX.delete(0, receivedTextHEX.length());
            receivedTextASC.delete(0, receivedTextASC.length());
        });

    }

    private void sendACK(byte[] mess) {
        actualPort.setDTR();
        actualPort.writeBytes(mess, mess.length);
        actualPort.clearDTR();
    }

    private void sending(byte[] bytes) {
        actualPort.setRTS();
        actualPort.setDTR();
        System.out.println("Sending...");
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        actualPort.writeBytes(bytes, bytes.length);
        actualPort.clearRTS();
        actualPort.clearDTR();
    }

    private void checkFCS(byte fcs1, byte fcs2, int len, byte command, byte[] data) {
        short decodedFCS1 = Short.parseShort(String.format("%02x",fcs1Byte), 16);
        short decodedFCS2 = Short.parseShort(String.format("%02x",fcs2Byte), 16);
        short moveByte = (short)(decodedFCS2<<8);
        short decodedFCS = (short)(moveByte+decodedFCS1);
        short fcsTest = 0;
        fcsTest += len;
        fcsTest += command;
        for (int i = 0; i < data.length; i++) {
            fcsTest += data[i];
        }
        if (fcs2 != 0x00) {
            if(fcsTest==(decodedFCS))
                System.out.println("Suma kontrolna prawidłowa");
        }
        else if(fcsTest==fcs1)
            System.out.println("Suma kontrolna prawidłowa");

    }

    private byte[] packToFrame(byte[] message) {
        byte[] frame = new byte[message.length + 6]; // +6 couse start,len,cc,bajtMOD,fcs,fcs2
        int frameIndex = 4;
        short fcsframe = 0;
        int lenData = message.length;
        frame[0] = 0x02;
        if (bpskB.isSelected() || qpskB.isSelected() || epskB.isSelected()) {
            lenData = lenData+1;
            frame[1] = (byte) (lenData);
            fcsframe += modByte;
            frame[3] = (byte) modByte;
        } else {
            frame[1] = (byte) lenData;
        }
        if (phyb.isSelected()) {
            frame[2] = 0x24;
            fcsframe += 0x24;
        } else if (dlb.isSelected()) {
            frame[2] = 0x50;
            fcsframe += 0x50;
        }
        for (int i = 0; i < message.length; i++) {
            fcsframe += message[i];
            frame[frameIndex++] = message[i];
        }
        fcsframe+=lenData;
        if (fcsframe > 0xff) {
            frame[message.length + 4] = (byte) (fcsframe & 255);
            frame[message.length + 5] = (byte) (fcsframe >> 8);
        }
        else if (fcsframe < 0xff) {
            frame[message.length + 4] = (byte) fcsframe;
            frame[message.length + 5] = 0x00;
        }
        return frame;
    }
}
