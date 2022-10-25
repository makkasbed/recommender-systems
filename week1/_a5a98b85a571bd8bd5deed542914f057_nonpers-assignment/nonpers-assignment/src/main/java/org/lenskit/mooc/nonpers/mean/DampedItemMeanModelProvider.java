package org.lenskit.mooc.nonpers.mean;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.lenskit.baseline.MeanDamping;
import org.lenskit.data.dao.DataAccessObject;
import org.lenskit.data.ratings.Rating;
import org.lenskit.inject.Transient;
import org.lenskit.util.io.ObjectStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Provider class that builds the mean rating item scorer, computing damped item means from the
 * ratings in the DAO.
 */
public class DampedItemMeanModelProvider implements Provider<ItemMeanModel> {
    /**
     * A logger that you can use to emit debug messages.
     */
    private static final Logger logger = LoggerFactory.getLogger(DampedItemMeanModelProvider.class);

    /**
     * The data access object, to be used when computing the mean ratings.
     */
    private final DataAccessObject dao;
    /**
     * The damping factor.
     */
    private final double damping;

    /**
     * Constructor for the mean item score provider.
     *
     * <p>The {@code @Inject} annotation tells LensKit to use this constructor.
     *
     * @param dao The data access object (DAO), where the builder will get ratings.  The {@code @Transient}
     *            annotation on this parameter means that the DAO will be used to build the model, but the
     *            model will <strong>not</strong> retain a reference to the DAO.  This is standard procedure
     *            for LensKit models.
     * @param damping The damping factor for Bayesian damping.  This is number of fake global-mean ratings to
     *                assume.  It is provided as a parameter so that it can be reconfigured.  See the file
     *                {@code damped-mean.groovy} for how it is used.
     */
    @Inject
    public DampedItemMeanModelProvider(@Transient DataAccessObject dao,
                                       @MeanDamping double damping) {
        this.dao = dao;
        this.damping = damping;
    }

    /**
     * Construct an item mean model.
     *
     * <p>The {@link Provider#get()} method constructs whatever object the provider class is intended to build.</p>
     *
     * @return The item mean model with mean ratings for all items.
     */
    @Override
    public ItemMeanModel get() {
        long count = 0;
        double total = 0.0;

        Long2DoubleMap itemRatingSums = new Long2DoubleOpenHashMap();
        Long2IntMap itemRatingCounts = new Long2IntOpenHashMap();

        itemRatingSums.defaultReturnValue(0.0);
        itemRatingCounts.defaultReturnValue(0);

        try (ObjectStream<Rating> ratings = dao.query(Rating.class).stream()) {
            for (Rating r: ratings) {
                // this loop will run once for each rating in the data set
                long i = r.getItemId();

                count++;
                total += r.getValue();

                itemRatingSums.put(i, r.getValue() + itemRatingSums.get(i));
                itemRatingCounts.put(i, itemRatingCounts.get(i) + 1);
            }
        }

        double mean = count > 0 ? total / count : 0;

        Long2DoubleOpenHashMap dampedMeans = new Long2DoubleOpenHashMap();
        for(Long2DoubleMap.Entry itemRatingSum: itemRatingSums.long2DoubleEntrySet()) {
            long i = itemRatingSum.getLongKey();
            double itemCount = itemRatingCounts.get(i);

            dampedMeans.put(i, (itemRatingSum.getDoubleValue() + damping * mean) / (itemCount + damping));
        }

        // Check results:
        logger.info("item [1203] " + dampedMeans.get(1203));
        logger.info("item [2959] " + dampedMeans.get(2959));

        logger.info("computed damped mean ratings for {} items", dampedMeans.size());
        return new ItemMeanModel(dampedMeans);
    }
}
