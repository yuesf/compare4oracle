package compare.core;

import info.monitorenter.cpdetector.io.ASCIIDetector;
import info.monitorenter.cpdetector.io.CodepageDetectorProxy;
import info.monitorenter.cpdetector.io.JChardetFacade;
import info.monitorenter.cpdetector.io.ParsingDetector;
import info.monitorenter.cpdetector.io.UnicodeDetector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.Maps;

import compare.beans.definition.Mail;
import compare.context.DifferenceIndex;
import compare.context.DifferenceTable;
import compare.context.NoOwner;

/**
 * @author   yueshanfei
 * @date  2016年9月22日
 */
public class MailHandler {
    protected static final Logger logger = LogManager.getLogger();
    
    private Mail mail;
    private String outputhtml;
    public MailHandler(Mail mail) {
        this.mail = mail;
        outputhtml = getOutputPath();
    }
    
    public void sendNoOwnerMail(List<NoOwner> nws) throws Exception {
        String filename = "ErrorInformation.html";
        String charsetName = getCharsetName(filename);
        StringBuffer tr = new StringBuffer();
        for (NoOwner no : nws) {
            System.out.println("not owner:" + no.getTableName());
            tr.append("<tr><th></th><td>").append(no.getPath()).append("</td>");
            tr.append("<td>").append(no.getTableName()).append("</td>");
            tr.append("</tr>");
        }
        
        try {
            logger.info("write error File...");
            String body = readBody(filename, charsetName);
            body = body.replaceAll("#wupf1", getCurrDateTime());
            String rows = java.util.regex.Matcher.quoteReplacement(tr.toString());
            body = body.replaceAll("#wupf4", rows);
            
            // ouput html
            String file = outputhtml + File.separator + "no_owner.html";
            logger.debug("save file path = " + new File(file).getAbsolutePath());
            saveFile(file, body, charsetName);
            
            if (null != mail) {
                String subject = "读取PDM文件无Owner邮件(自动发出)";
                sendMail(mail.getReveicer(), subject, body, "GBK");
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
            logger.error(ex);
            throw ex;
        }
    }
    
    public void sendTableMail(List<DifferenceTable> errors, CompareResult result) throws Exception {
        sortTable(errors);
        String filename = "compare_table.html";
        String charsetName = getCharsetName(filename);
        StringBuffer tr = new StringBuffer();
        LinkedHashMap<String, Integer> totalMap = Maps.newLinkedHashMap();
        for (DifferenceTable t : errors) {
            String typeId = t.getTypeId();
            if (totalMap.containsKey(typeId)) {
                totalMap.put(typeId, totalMap.get(typeId) + 1);
            }
            else {
                totalMap.put(typeId, 1);
            }
            tr.append("<tr><th>").append(typeId).append("</th>");
            if (t.isSubmeter()) {
                tr.append("<td>")
                        .append(t.getSourseTable() + "<br>" + t.getParentTable() + "<br>(" + t.getPdmfile() + ")")
                        .append("</td>");
            }
            else {
                tr.append("<td>").append(t.getSourseTable() + "<br>(" + t.getPdmfile() + ")").append("</td>");
            }
            tr.append("<td>").append(t.getSourseField()).append("</td>");
            tr.append("<td>").append(t.getSourseType()).append("</td>");
            tr.append("<td>").append(t.getSourseDefault()).append("</td>");
            tr.append("<td>").append(t.getSourceNullabel()).append("</td>");
            tr.append("<td>").append(t.getCompareTable() + "<br>(" + t.getOwner() + ")").append("</td>");
            tr.append("<td>").append(t.getCompareField()).append("</td>");
            tr.append("<td>").append(t.getCompareType()).append("</td>");
            tr.append("<td>").append(t.getCompareDefault()).append("</td>");
            tr.append("<td>").append(t.getCompareNullable()).append("</td>");
            tr.append("<td>").append(t.getReferSQL()).append("</td>");
            tr.append("</tr>");
        }
        logger.debug("write Result File...");
        String readBody = readBody(filename,charsetName);
        readBody = readBody.replaceAll("#sourceTables", result.getSourceNumber() + "");
        readBody = readBody.replaceAll("#compareTables", result.getCompareNumber() + "");
        readBody = readBody.replaceAll("#diffRate", result.getDiffrate() + "");
        readBody = readBody.replaceAll("#diffTables", result.getDiffNumber() + "");
        readBody = readBody.replaceAll("#wupf1", getCurrDateTime());
        readBody = readBody.replaceAll("#wupf2", result.getSourceContent());
        readBody = readBody.replaceAll("#wupf3", result.getCompareContent());
        
        String rows = java.util.regex.Matcher.quoteReplacement(tr.toString());
        readBody = readBody.replaceAll("#wupf4", rows);
        //统计差异类型数量
        String total = "<tr>";
        for (int i = 1; i <= 10; i++) {
            Integer integer = totalMap.get(i + "");
            total += "<td>" + (null == integer ? 0 : integer) + "</td>";
        }
        total += "</tr>";
        readBody = readBody.replaceAll("#diffs", total);
        //初始化
        String file = outputhtml + File.separator + "table.html";
        logger.debug("save file path = " + new File(file).getAbsolutePath());
        saveFile(file, readBody, charsetName);
        
        if (null != mail) {
            logger.debug("send mail to compare table result.");
            String subject = "比对表结果(自动发出)/" + result.getCompareContent();
            sendMail(mail.getReveicer(), subject, readBody, "GBK");
        }
    }
    
    private void sortTable(List<DifferenceTable> errors) {

        Collections.sort(errors, new Comparator<DifferenceTable>() {

            public int compare(DifferenceTable o1, DifferenceTable o2) {
                return o1.getTypeId().compareTo(o2.getTypeId());
            }
        });
    }

    public void sendIndexMail(List<DifferenceIndex> errors, CompareResult result) throws Exception {
        sortIndex(errors);
        String filename = "compare_index.html";
        String charsetName = getCharsetName(filename);
        StringBuffer tr = new StringBuffer();
        LinkedHashMap<String, Integer> totalMap = Maps.newLinkedHashMap();
        for (DifferenceIndex i : errors) {
            String typeId = i.getTypeId();
            if (totalMap.containsKey(typeId)) {
                totalMap.put(typeId, totalMap.get(typeId) + 1);
            }
            else {
                totalMap.put(typeId, 1);
            }
            tr.append("<tr><th>").append(typeId).append("</th>");
            if (i.isSubmeter()) {
                tr.append("<td>").append(i.getSourceTableName() + "<br>" + i.getParentTable())
                        .append("</td>");
            }
            else {
                tr.append("<td>").append(i.getSourceTableName()).append("</td>");
            }
            tr.append("<td>").append(i.getSourceIndexName()).append("</td>");
            tr.append("<td>").append(i.getSourceField()).append("</td>");
            tr.append("<td>").append(i.getSourceUnique()).append("</td>");
            tr.append("<td>").append(i.getCompareTableName()+ "<br>(" + i.getOwner() + ")").append("</td>");
            tr.append("<td>").append(i.getCompareIndexName()).append("</td>");
            tr.append("<td>").append(i.getCompareField()).append("</td>");
            tr.append("<td>").append(i.getCompareUnique()).append("</td>");
            tr.append("<td>").append(i.getReferSQL()).append("</td>");
            tr.append("</tr>");
        }
        logger.debug("write Result File...");
        String readBody = readBody(filename, charsetName);
        readBody = readBody.replaceAll("#wupf1", getCurrDateTime());
        readBody = readBody.replaceAll("#wupf2", result.getSourceContent());
        readBody = readBody.replaceAll("#wupf3", result.getCompareContent());
        String rows = java.util.regex.Matcher.quoteReplacement(tr.toString());
        readBody = readBody.replaceAll("#wupf4", rows);
        //统计差异类型数量
        String total = "<tr>";
        for (int i = 11; i <= 15; i++) {
            Integer integer = totalMap.get(i + "");
            total += "<td>" + (null == integer ? 0 : integer) + "</td>";
        }
        total += "</tr>";
        readBody = readBody.replaceAll("#diffs", total);
        //初始化
        String file = outputhtml + File.separator + "index.html";
        logger.debug("save file path = " + new File(file).getAbsolutePath());
        saveFile(file, readBody, charsetName);
        
        if (null != mail) {
            logger.debug("send mail to compare index result.");
            String subject = "比对索引结果(自动发出)/" + result.getCompareContent();
            sendMail(mail.getReveicer(), subject, readBody, "GBK");
        }
    }
    
    private void sortIndex(List<DifferenceIndex> errors) {
        Collections.sort(errors, new Comparator<DifferenceIndex>() {
            
            public int compare(DifferenceIndex o1, DifferenceIndex o2) {
                return o1.getTypeId().compareTo(o2.getTypeId());
            }
        });
        
    }

    private String readBody(String filename,String charsetName) throws IOException {
        InputStreamReader read = null;
        BufferedReader reader = null;
        try {
            StringBuffer sb = new StringBuffer();
            read = new InputStreamReader(new FileInputStream(filename), charsetName);
            reader = new BufferedReader(read);
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\r\n");
            }
            return sb.toString();
        }
        catch (IOException e) {
            e.printStackTrace();
           throw e;
        }
        finally {
            if (null != reader)
                try {
                    reader.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            if (null != read)
                try {
                    read.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
        }
    }
    
    private void sendMail(String receiver, String subject, String body, String encode) {
        
        int iSendMailRetryCount = 0;
        try {
            Properties props = new Properties();
            Session sendMailSession;
            String[] strMailTos = receiver.split(",");
            String strMailTo = "";
            if (strMailTos.length > 0) {
                for (int i = 0; i < strMailTos.length; i++) {
                    if (strMailTos[i].length() > 0) {
                        if (-1 == strMailTo.indexOf(strMailTos[i])) {
                            if (strMailTos[i].endsWith("@asiainfo.com")) {
                                strMailTo += strMailTos[i] + ",";
                            }
                            else {
                                strMailTo += strMailTos[i] + "@asiainfo.com,";
                            }
                        }
                    }
                }
            }
            else {
                strMailTo += receiver + "@asiainfo..com";
            }
            logger.info(subject + ", send mail to:{}", strMailTo);
            sendMailSession = Session.getInstance(props, null);
            props.put("mail.smtp.host", mail.getStmp());
            props.put("mail.smtp.auth", "true");
            props.setProperty("mail.smtp.timeout", "80000");
            Message newMessage = new MimeMessage(sendMailSession);
            newMessage.setFrom(new InternetAddress(mail.getUser() + "@asiainfo.com"));
            newMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(strMailTo));
            newMessage.setSubject(subject);// 主题
            newMessage.setSentDate(new java.util.Date());
            if (encode == null || 0 == encode.length()) {
                encode = "UTF-8";
            }
            newMessage.setDataHandler(new DataHandler(body, "text/html;charset=" + encode));
            Transport transport = sendMailSession.getTransport("smtp");
            transport.connect(mail.getStmp(), mail.getUser(), mail.getPasswd());
            transport.sendMessage(newMessage, newMessage.getAllRecipients());
            transport.close();
            logger.info(subject + ", 发送邮件成功！");
        }
        catch (javax.mail.SendFailedException ex) {
            String strMail = ex.getMessage();
            if (strMail.indexOf("550") > 0) {
                if (strMail.indexOf("<") > 0 && iSendMailRetryCount < 3) {
                    strMail = strMail.substring(strMail.indexOf("<") + 1);
                    strMail = strMail.substring(0, strMail.indexOf(">"));
                    logger.info(strMail);
                    logger.info(receiver);
                    receiver = receiver.replaceAll(strMail, "");
                    sendMail(receiver, subject, body, encode);
                    iSendMailRetryCount++;
                }
            }
            logger.error(ex);
            
        }
        catch (Exception ex) {
            ex.printStackTrace();
            logger.error(ex);
        }
        
    }
    
    private String getOutputPath() {
        File f = new File("outputhtml");
        //处理前先清除
        if (f.exists()) {
            for (File file : f.listFiles()) {
                file.delete();
            }
        }
        try {
            f.mkdirs();
            return f.getCanonicalPath();
        }
        catch (IOException e) {
            e.printStackTrace();
            logger.error("获取pdm根路径出错!");
        }
        return null;
    }
    
    public static void saveFile(String strFileFullName, String str, String charsetName) {
        OutputStreamWriter fout = null;
        try {
            
            logger.debug("saving filename:" + strFileFullName);
            File uploadFilePath = new File(strFileFullName);
            File parent = uploadFilePath.getParentFile();
            if (!parent.exists()) {
                parent.mkdir();
            }
            fout =new  OutputStreamWriter(new FileOutputStream(strFileFullName), charsetName);
            fout.append(str);
            fout.flush();
            fout.close();
            logger.debug("saved file ok:" + strFileFullName);
        }
        catch (Exception ex) {
            logger.error(ex);
            ex.printStackTrace();
        }
        finally {
            if (fout != null) {
                try {
                    fout.close();
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
    
    public static String getCharsetName(String file) throws Exception {
        return getCharsetName(new File(file));
    }
    public static String getCharsetName(File file) throws Exception {
        /*------------------------------------------------------------------------ 
        detector是探测器，它把探测任务交给具体的探测实现类的实例完成。 
        cpDetector内置了一些常用的探测实现类，这些探测实现类的实例可以通过add方法 
        加进来，如ParsingDetector、 JChardetFacade、ASCIIDetector、UnicodeDetector。   
        detector按照“谁最先返回非空的探测结果，就以该结果为准”的原则返回探测到的 
        字符集编码。 
      --------------------------------------------------------------------------*/
      CodepageDetectorProxy detector = CodepageDetectorProxy.getInstance();
      /*------------------------------------------------------------------------- 
        ParsingDetector可用于检查HTML、XML等文件或字符流的编码,构造方法中的参数用于 
        指示是否显示探测过程的详细信息，为false不显示。 
      ---------------------------------------------------------------------------*/
      detector.add(new ParsingDetector(false));//如果不希望判断xml的encoding，而是要判断该xml文件的编码，则可以注释掉
      /*-------------------------------------------------------------------------- 
         JChardetFacade封装了由Mozilla组织提供的JChardet，它可以完成大多数文件的编码 
         测定。所以，一般有了这个探测器就可满足大多数项目的要求，如果你还不放心，可以 
         再多加几个探测器，比如下面的ASCIIDetector、UnicodeDetector等。 
        ---------------------------------------------------------------------------*/
      detector.add(JChardetFacade.getInstance());
      // ASCIIDetector用于ASCII编码测定
      detector.add(ASCIIDetector.getInstance());
      // UnicodeDetector用于Unicode家族编码的测定
      detector.add(UnicodeDetector.getInstance());
      Charset charset = null;
      try {
          charset = detector.detectCodepage(file.toURI().toURL());
      }
      catch (Exception ex) {
          ex.printStackTrace();
          logger.error(file.getName()+" This file charset name is error.");
      }
      if (charset != null) {
          logger.debug(file+", This file charset is:"+charset);
          
          // 探测后得到windows-1252，表示类型错误，需要将PDM另存为XML格式的PDM
          if ("windows-1252".equals(charset.name())) {
        	  throw new Exception("PDM文件编码格式不正确，需要另存为XML格式的PDM文件");
          }
         return charset.name();
      }
      else {
          logger.debug(file+", 文件编码格式取的默认值");
         return "GBK";
      }
    }
    private static String getCurrDateTime() {
        java.sql.Timestamp date = new java.sql.Timestamp(System.currentTimeMillis());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return formatter.format(date);
    }
}
