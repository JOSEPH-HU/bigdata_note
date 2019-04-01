
import com.hankcs.hanlp.corpus.io.IOUtil;
import com.hankcs.hanlp.corpus.tag.Nature;
import com.hankcs.hanlp.dictionary.BaseSearcher;
import com.hankcs.hanlp.dictionary.CoreDictionary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.*;
@Component
public class VipColorWordDic {
    private static Logger log = LoggerFactory.getLogger(VipColorWordDic.class);
    @Autowired
    private VipColorWordService vipColorWordService;

    DoubleArrayTrie<VipColorWordModel> trie = new DoubleArrayTrie<VipColorWordModel>();

    /**
     * 颜色词词典
     *
     * @return
     */
    public boolean update() {
        try {
            DoubleArrayTrie<VipColorWordModel> temp = new DoubleArrayTrie<VipColorWordModel>();
            TreeMap<String, VipColorWordModel> map = new TreeMap<String, VipColorWordModel>();
            Map<String,String> singlecolors = new HashMap<>();
            VipColorWordModel model = new VipColorWordModel();
            model.setIsDeleted((byte) 0);
            model.setStatus((byte) 1);
            try {
                int total = vipColorWordService.selectCount(model);
                if (total == 0) {
                    log.error("颜色词词表为空");
                    return false;
                }
                PageModel<VipColorWordModel> pm = new PageModel<>();
                Map<String,Integer> param = new HashMap<>();
                int pageSize = Constant.GET_DATABASE_DEFAULT_NUM;
                pm.setPageSize(pageSize);
                pm.setTotalRecords(total);
                while (!pm.isBottonPage()) {
                    int cPage = pm.getNextPageNo();
                    param.put("start", cPage * pageSize);
                    param.put("limit", pageSize);
                    pm.setPageNo(cPage + 1);
                    List<VipColorWordModel> words = vipColorWordService.findColorWordByPage(param);
                    log.info("get color word  start {} limit {} ---> list  size --->{}", param.get("start"),
                            param.get("limit"), words.size());
                    for (VipColorWordModel cw : words) {
                        String word = cw.getWord();
                        word = word.toLowerCase().trim();

                        if (cw.getType() == 2) {
                            singlecolors.put(word, String.valueOf(cw.getType()));
                        } else {
                            //加到主词典
                            map.put(word,cw);
                            CoreDictionary.Attribute attribute = new CoreDictionary.Attribute(1);
        					attribute.nature[0] = Enum.valueOf(Nature.class,"n");
        					attribute.frequency[0] = Constant.wordNatureFreq;
        					attribute.totalFrequency += attribute.frequency[0];
        					Constant.NEWWORDSMAP.put(word, attribute);//放到这个字典
                        }
                    }
                }
            } catch (Exception e) {
                log.error("加载颜色词典失败{}",e);
            }
            if (!map.isEmpty()) {
                Constant.SINGLECOLORWORD.getAndSet(singlecolors);
                int resultCode = temp.build(map);
                log.info("build result :{}", resultCode);
                trie = temp;
            }
        } catch (Exception e) {
            log.error("更新颜色词典出现错误,原因{}", e);
            return false;
        }
        log.info("更新颜色词典成功,共{}条", trie.getKeySize());
        return true;
    }

    /**
     * 查询一个单词
     *
     * @param key
     * @return 单词对应的条目
     */
    public VipColorWordModel get(String key) {
        if (trie == null || trie.getSize() == 0) {
            return null;
        }
        return trie.get(key);
    }

    /**
     * 是否含有键
     *
     * @param key
     * @return
     */
    public boolean contains(String key) {
        return get(key) != null;
    }

    /**
     * 词典大小
     *
     * @return
     */
    public int size() {
        return trie.size();
    }

    /**
     * 排序这个词典
     *
     * @param path
     * @return
     */
    public static boolean sort(String path) {
        TreeMap<String, String> map = new TreeMap<String, String>();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    IOUtil.newInputStream(path), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                String[] argArray = line.split("\\s");
                map.put(argArray[0], line);
            }
            br.close();
            // 输出它们
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                    IOUtil.newOutputStream(path)));
            for (Map.Entry<String, String> entry : map.entrySet()) {
                bw.write(entry.getValue());
                bw.newLine();
            }
            bw.close();
        } catch (Exception e) {
            log.error("读取" + path + "失败" + e);
            return false;
        }
        return true;
    }

    public BaseSearcher getSearcher(String text) {
        return new Searcher(text);
    }

    /**
     * 前缀搜索，长短都可匹配
     */
    public class Searcher extends BaseSearcher<VipColorWordModel> {
        /**
         * 分词从何处开始，这是一个状态
         */
        int begin;

        private List<Map.Entry<String, VipColorWordModel>> entryList;

        protected Searcher(char[] c) {
            super(c);
        }

        protected Searcher(String text) {
            super(text);
            entryList = new LinkedList<Map.Entry<String, VipColorWordModel>>();
        }

        @Override
        public Map.Entry<String, VipColorWordModel> next() {
            // 保证首次调用找到一个词语
            while (entryList.size() == 0 && begin < c.length) {
                entryList = trie.commonPrefixSearchWithValue(c, begin);
                ++begin;
            }
            // 之后调用仅在缓存用完的时候调用一次
            if (entryList.size() == 0 && begin < c.length) {
                entryList = trie.commonPrefixSearchWithValue(c, begin);
                ++begin;
            }
            if (entryList.size() == 0) {
                return null;
            }
            Map.Entry<String, VipColorWordModel> result = entryList.get(0);
            entryList.remove(0);
            offset = begin - 1;
            return result;
        }
    }
}
