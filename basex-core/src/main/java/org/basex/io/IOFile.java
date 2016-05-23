package org.basex.io;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import javax.xml.transform.stream.*;

import org.basex.io.out.*;
import org.basex.util.*;
import org.basex.util.list.*;
import org.xml.sax.*;

/**
 * {@link IO} reference, representing a local file or directory path.
 *
 * @author BaseX Team 2005-16, BSD License
 * @author Christian Gruen
 */
public final class IOFile extends IO {
  /** Pattern for absolute file paths. */
  public static final Pattern ABSOLUTE =
      Pattern.compile(Prop.WIN ? "^(/|\\|[a-zA-Z]:).*$" : "^/.*");
  /** Pattern for valid file names. */
  private static final Pattern VALIDNAME =
      Pattern.compile("^[^\\\\/" + (Prop.WIN ? ":*?\"<>\\|" : "") + "]+$");
  /** File reference. */
  private final File file;
  /** Indicate if supplied path was absolute. */
  private final boolean absolute;

  /**
   * Constructor.
   * @param file file reference
   * @param original original path
   */
  private IOFile(final File file, final String original) {
    super(create(file.getAbsolutePath()));
    absolute = file.isAbsolute();
    this.file = absolute ? file : file.getAbsoluteFile();
    // preserve trailing slash
    if(original.endsWith("/") || original.endsWith("\\")) pth += '/';
  }

  /**
   * Constructor.
   * @param file file reference
   */
  public IOFile(final File file) {
    this(file, "");
  }

  /**
   * Constructor.
   * @param path file path
   */
  public IOFile(final String path) {
    this(new File(path), path);
  }

  /**
   * Constructor.
   * @param dir parent directory string
   * @param child child directory string
   */
  public IOFile(final String dir, final String child) {
    this(new File(dir, child), child);
  }

  /**
   * Constructor.
   * @param dir directory string
   * @param child child path string
   */
  public IOFile(final IOFile dir, final String child) {
    this(new File(dir.file, child), child);
  }

  /**
   * Returns the file reference.
   * @return file reference
   */
  public File file() {
    return file;
  }

  /**
   * Creates a new instance of this file.
   * @return success flag
   */
  public boolean touch() {
    try {
      Files.createFile(toPath());
      return true;
    } catch(final IOException ex) {
      Util.debug(ex);
      return false;
    }
  }

  @Override
  public byte[] read() throws IOException {
    return Files.readAllBytes(toPath());
  }

  @Override
  public boolean exists() {
    return file.exists();
  }

  @Override
  public boolean isDir() {
    return file.isDirectory();
  }

  @Override
  public boolean isAbsolute() {
    return absolute;
  }

  @Override
  public long timeStamp() {
    return file.lastModified();
  }

  @Override
  public long length() {
    return file.length();
  }

  @Override
  public InputSource inputSource() {
    return new InputSource(url());
  }

  @Override
  public StreamSource streamSource() {
    return new StreamSource(pth);
  }

  @Override
  public InputStream inputStream() throws IOException {
    return new FileInputStream(file);
  }

  /**
   * Resolves two paths.
   * @param path file path (relative or absolute)
   * @return resulting path
   */
  public IOFile resolve(final String path) {
    final File f = new File(path);
    return f.isAbsolute() ? new IOFile(f) : new IOFile(isDir() ? pth : dir(), path);
  }

  /**
   * Recursively creates the directory if it does not exist yet.
   * @return {@code true} if the directory exists or has been created.
   */
  public boolean md() {
    return file.exists() || file.mkdirs();
  }

  /**
   * Returns the parent of this file or directory or {@code null} if there is no parent directory.
   * @return directory or {@code null}
   */
  public IOFile parent() {
    final String parent = file.getParent();
    return parent == null ? null : new IOFile(parent);
  }

  /**
   * Returns the children of the path.
   * @return children
   */
  public IOFile[] children() {
    return children(".*");
  }

  /**
   * Returns the children of the path that match the specified regular expression.
   * @param regex regular expression pattern
   * @return children
   */
  public IOFile[] children(final String regex) {
    final File[] ch = file.listFiles();
    if(ch == null) return new IOFile[0];

    final ArrayList<IOFile> io = new ArrayList<>(ch.length);
    final Pattern p = Pattern.compile(regex, Prop.CASE ? 0 : Pattern.CASE_INSENSITIVE);
    for(final File f : ch) {
      if(p.matcher(f.getName()).matches()) io.add(new IOFile(f));
    }
    return io.toArray(new IOFile[io.size()]);
  }

  /**
   * Returns the relative paths of all descendant files (excluding directories).
   * @return relative paths
   */
  public synchronized StringList descendants() {
    final StringList files = new StringList();
    final File[] ch = file.listFiles();
    if(ch == null) return files;
    if(exists()) addDescendants(this, files, path().length() + 1);
    return files;
  }

  /**
   * Writes the specified byte array.
   * @param bytes bytes
   * @throws IOException I/O exception
   */
  public void write(final byte[] bytes) throws IOException {
    Files.write(toPath(), bytes);
  }

  /**
   * Writes the specified input. The specified input stream is eventually closed.
   * @param in input stream
   * @throws IOException I/O exception
   */
  public void write(final InputStream in) throws IOException {
    try(final BufferOutput out = new BufferOutput(pth)) {
      for(int i; (i = in.read()) != -1;) out.write(i);
    }
  }

  /**
   * Deletes the file or directory.
   * @return {@code true} if the file does not exist or has been deleted.
   */
  public boolean delete() {
    boolean ok = true;
    if(file.exists()) {
      if(isDir()) {
        for(final IOFile ch : children()) ok &= ch.delete();
      }
      try {
        Files.delete(toPath());
      } catch(final IOException ex) {
        Util.debug(ex);
        return false;
      }
    }
    return ok;
  }

  /**
   * Renames a file to the specified path. The path must not exist yet.
   * @param target target reference
   * @return success flag
   */
  public boolean rename(final IOFile target) {
    return file.renameTo(target.file);
  }

  /**
   * Copies a file to another target.
   * @param target target
   * @throws IOException I/O exception
   */
  public void copyTo(final IOFile target) throws IOException {
    // create parent directory of target file
    target.parent().md();
    Files.copy(toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
  }

  @Override
  public boolean eq(final IO io) {
    return io instanceof IOFile && (Prop.CASE ? pth.equals(io.pth) :
      pth.equalsIgnoreCase(io.pth));
  }

  @Override
  public String url() {
    final TokenBuilder tb = new TokenBuilder(FILEPREF);
    // add leading slash for Windows paths
    if(!pth.startsWith("/")) tb.add("///");
    final int pl = pth.length();
    for(int p = 0; p < pl; p++) {
      // replace spaces with %20
      final char ch = pth.charAt(p);
      if(ch == ' ') tb.add("%20");
      else tb.add(ch);
    }
    return tb.toString();
  }

  /**
   * Opens the file externally.
   * @throws IOException I/O exception
   */
  public void open() throws IOException {
    final String[] args;
    if(Prop.WIN) {
      args = new String[] { "rundll32", "url.dll,FileProtocolHandler", pth };
    } else if(Prop.MAC) {
      args = new String[] { "/usr/bin/open", pth };
    } else {
      args = new String[] { "xdg-open", pth };
    }
    new ProcessBuilder(args).directory(parent().file).start();
  }

  /**
   * Returns a native file path representation. If normalization fails, returns the original path.
   * @return path
   */
  public IOFile normalize() {
    try {
      return new IOFile(toPath().toRealPath().toFile());
    } catch(final IOException ex) {
      return this;
    }
  }

  // STATIC METHODS ===============================================================================

  /**
   * Returns a {@link Path} instance of this file.
   * @return path
   * @throws IOException I/O exception
   */
  private Path toPath() throws IOException {
    try {
      return Paths.get(pth);
    } catch(final InvalidPathException ex) {
      Util.debug(ex);
      throw new IOException(ex);
    }
  }

  /**
   * Adds the relative paths of all descendant files to the specified list.
   * @param io current file
   * @param files file list
   * @param offset string length of root path
   */
  private static void addDescendants(final IOFile io, final StringList files, final int offset) {
    if(io.isDir()) {
      for(final IOFile f : io.children()) addDescendants(f, files, offset);
    } else {
      files.add(io.path().substring(offset));
    }
  }

  /**
   * Checks if the specified string is a valid file name.
   * @param name file name
   * @return result of check
   */
  public static boolean isValidName(final String name) {
    return VALIDNAME.matcher(name).matches();
  }

  /**
   * Checks if the specified string is a valid file reference.
   * @param path path string
   * @return result of check
   */
  public static boolean isValid(final String path) {
    // no colon: treat as file path
    final int c = path.indexOf(':');
    if(c == -1) return true;
    // Windows drive letter?
    final int fs = path.indexOf('/'), bs = path.indexOf('\\');
    if(Prop.WIN && c == 1 && Token.letter(path.charAt(0)) && (fs == 2 || bs == 2)) return true;
    // ensure that slash occurs before colon
    return fs != -1 && fs < c || bs != -1 && bs < c;
  }

  /**
   * Converts a name filter (glob) to a regular expression.
   * @param glob filter
   * @return regular expression
   */
  public static String regex(final String glob) {
    return regex(glob, true);
  }

  /**
   * Converts a file filter (glob) to a regular expression. A filter may
   * contain asterisks (*) and question marks (?); commas (,) are used to
   * separate multiple filters.
   * @param glob filter
   * @param sub accept substring in the result
   * @return regular expression
   */
  public static String regex(final String glob, final boolean sub) {
    final StringBuilder sb = new StringBuilder();
    for(final String globs : Strings.split(glob, ',')) {
      final String glb = globs.trim();
      if(sb.length() != 0) sb.append('|');
      // loop through single pattern
      boolean suf = false;
      final int gl = glb.length();
      for(int g = 0; g < gl; g++) {
        char ch = glb.charAt(g);
        if(ch == '*') {
          // don't allow other dots if pattern ends with a dot
          suf = true;
          sb.append(glb.endsWith(".") ? "[^.]" : ".");
        } else if(ch == '?') {
          ch = '.';
          suf = true;
        } else if(ch == '.') {
          suf = true;
          // last character is dot: disallow file suffix
          if(g + 1 == glb.length()) break;
          sb.append('\\');
        } else if(!Character.isLetterOrDigit(ch)) {
          sb.append('\\');
        }
        sb.append(ch);
      }
      if(!suf && sub) sb.append(".*");
    }
    return Prop.CASE ? sb.toString() : sb.toString().toLowerCase(Locale.ENGLISH);
  }

  // PRIVATE METHODS ==============================================================================

  /**
   * Creates a path.
   * @param path input path
   * @return path
   */
  private static String create(final String path) {
    final StringList sl = new StringList();
    final int l = path.length();
    final TokenBuilder tb = new TokenBuilder(l);
    for(int i = 0; i < l; ++i) {
      final char ch = path.charAt(i);
      if(ch == '\\' || ch == '/') add(tb, sl);
      else tb.add(ch);
    }
    add(tb, sl);
    if(path.startsWith("\\\\") || path.startsWith("//")) tb.add("//");
    final int size = sl.size();
    for(int s = 0; s < size; ++s) {
      if(s != 0 || path.startsWith("/")) tb.add('/');
      tb.add(sl.get(s));
    }
    return tb.toString();
  }

  /**
   * Adds a directory/file to the path list.
   * @param tb entry to be added
   * @param sl string list
   */
  private static void add(final TokenBuilder tb, final StringList sl) {
    String s = tb.toString();
    // switch first Windows letter to upper case
    if(s.length() > 1 && s.charAt(1) == ':' && sl.isEmpty()) {
      s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
    if("..".equals(s) && !sl.isEmpty()) {
      // parent step
      if(sl.get(sl.size() - 1).indexOf(':') == -1) sl.remove(sl.size() - 1);
    } else if(!".".equals(s) && !s.isEmpty()) {
      // skip self and empty steps
      sl.add(s);
    }
    tb.reset();
  }

}
