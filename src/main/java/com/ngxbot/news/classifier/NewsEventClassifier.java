package com.ngxbot.news.classifier;

import com.ngxbot.common.model.EventType;
import com.ngxbot.data.entity.NewsItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Classifies news items into one or more {@link EventType} values using
 * regex-based keyword matching against headlines and summaries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsEventClassifier {

    private static final Map<EventType, List<Pattern>> EVENT_PATTERNS;

    static {
        Map<EventType, List<Pattern>> map = new EnumMap<>(EventType.class);

        map.put(EventType.EARNINGS_RELEASE, compilePatterns(
                "earnings", "profit", "revenue", "quarterly results",
                "annual report", "financial statement", "half[- ]?year results",
                "full[- ]?year results", "audited results", "unaudited results",
                "income statement", "EPS"
        ));

        map.put(EventType.DIVIDEND_ANNOUNCEMENT, compilePatterns(
                "dividend", "interim dividend", "final dividend",
                "dividend payment", "dividend declaration", "cash dividend",
                "dividend yield"
        ));

        map.put(EventType.RIGHTS_ISSUE, compilePatterns(
                "rights issue", "rights offering", "rights\\s+issue"
        ));

        map.put(EventType.STOCK_SPLIT, compilePatterns(
                "stock split", "share split"
        ));

        map.put(EventType.BONUS_ISSUE, compilePatterns(
                "bonus issue", "bonus share", "scrip issue", "bonus\\s+shares?"
        ));

        map.put(EventType.ACQUISITION_MERGER, compilePatterns(
                "acquisition", "merger", "takeover", "buyout",
                "M&A", "M\\s*&\\s*A", "acquire", "merged with",
                "business combination"
        ));

        map.put(EventType.REGULATORY_ACTION, compilePatterns(
                "\\bSEC\\b", "regulatory", "sanction", "\\bfine\\b",
                "penalty", "compliance", "regulatory action",
                "securities.+exchange.+commission"
        ));

        map.put(EventType.MANAGEMENT_CHANGE, compilePatterns(
                "\\bCEO\\b", "\\bMD\\b", "managing director", "\\bboard\\b",
                "appointment", "resign", "sack", "\\bCFO\\b", "\\bCOO\\b",
                "new chairman", "director.+appointed", "step.+down"
        ));

        map.put(EventType.INSIDER_TRADE, compilePatterns(
                "insider", "director dealing", "Form\\s*29",
                "share purchase by director", "director.+(?:bought|sold|acquired|disposed)",
                "insider trading", "insider transaction"
        ));

        map.put(EventType.CREDIT_RATING_CHANGE, compilePatterns(
                "credit rating", "Moody", "\\bFitch\\b", "\\bS&P\\b",
                "rating.+(?:upgrade|downgrade)", "(?:upgrade|downgrade).+rating",
                "credit.+(?:upgrade|downgrade)", "(?:upgrade|downgrade).+credit"
        ));

        map.put(EventType.SECTOR_NEWS, compilePatterns(
                "\\bsector\\b", "\\bindustry\\b", "banking sector",
                "oil and gas", "industrial sector", "consumer goods sector",
                "financial sector", "insurance sector"
        ));

        map.put(EventType.CBN_POLICY, compilePatterns(
                "\\bCBN\\b", "central bank", "monetary policy",
                "\\bMPR\\b", "\\bCRR\\b", "interest rate",
                "central bank of nigeria", "monetary policy rate",
                "cash reserve ratio"
        ));

        map.put(EventType.TRADING_SUSPENSION, compilePatterns(
                "suspend", "trading halt", "suspension",
                "trading.+suspend", "suspend.+trading"
        ));

        map.put(EventType.DELISTING_NOTICE, compilePatterns(
                "delist", "delisting", "voluntary delisting",
                "removal from.+list"
        ));

        map.put(EventType.AGM_EGM_NOTICE, compilePatterns(
                "\\bAGM\\b", "\\bEGM\\b", "annual general meeting",
                "extraordinary general meeting", "shareholder meeting",
                "general meeting"
        ));

        map.put(EventType.SHARE_BUYBACK, compilePatterns(
                "buyback", "share buyback", "repurchase",
                "share repurchase", "stock buyback"
        ));

        EVENT_PATTERNS = Collections.unmodifiableMap(map);
    }

    /**
     * Classifies a {@link NewsItem} into zero or more {@link EventType} values
     * based on its title and summary.
     *
     * @param item the news item to classify
     * @return list of matching event types, empty if none match
     */
    public List<EventType> classify(NewsItem item) {
        if (item == null) {
            return List.of();
        }

        String text = buildSearchText(item);
        List<EventType> matched = matchPatterns(text);

        if (!matched.isEmpty()) {
            log.debug("Classified news item id={} title='{}' as {}", item.getId(), item.getTitle(), matched);
        }

        return matched;
    }

    /**
     * Classifies a raw headline string into zero or more {@link EventType} values.
     *
     * @param headline the headline text to classify
     * @return list of matching event types, empty if none match
     */
    public List<EventType> classifyHeadline(String headline) {
        if (headline == null || headline.isBlank()) {
            return List.of();
        }

        List<EventType> matched = matchPatterns(headline);

        if (!matched.isEmpty()) {
            log.debug("Classified headline '{}' as {}", headline, matched);
        }

        return matched;
    }

    // ---- internal helpers ----

    private String buildSearchText(NewsItem item) {
        StringBuilder sb = new StringBuilder();
        if (item.getTitle() != null) {
            sb.append(item.getTitle());
        }
        if (item.getSummary() != null) {
            sb.append(' ').append(item.getSummary());
        }
        return sb.toString();
    }

    private List<EventType> matchPatterns(String text) {
        List<EventType> results = new ArrayList<>();

        for (Map.Entry<EventType, List<Pattern>> entry : EVENT_PATTERNS.entrySet()) {
            for (Pattern pattern : entry.getValue()) {
                if (pattern.matcher(text).find()) {
                    results.add(entry.getKey());
                    break; // one match per EventType is enough
                }
            }
        }

        return Collections.unmodifiableList(results);
    }

    private static List<Pattern> compilePatterns(String... keywords) {
        List<Pattern> patterns = new ArrayList<>(keywords.length);
        for (String kw : keywords) {
            patterns.add(Pattern.compile(kw, Pattern.CASE_INSENSITIVE));
        }
        return Collections.unmodifiableList(patterns);
    }
}
