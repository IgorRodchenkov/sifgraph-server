package org.pathwaycommons.sif.server;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.pathwaycommons.sif.io.Loader;
import org.pathwaycommons.sif.model.RelationTypeEnum;
import org.pathwaycommons.sif.model.SIFGraph;
import org.pathwaycommons.sif.query.Direction;
import org.pathwaycommons.sif.query.QueryExecutor;
import org.pathwaycommons.sif.util.EdgeAnnotationType;
import org.pathwaycommons.sif.util.RelationTypeSelector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

@RestController
@RequestMapping(value = "/v1", method = RequestMethod.GET, produces = MediaType.TEXT_PLAIN_VALUE)
public class Controller {

  private SIFGraph graph;
  private org.pathwaycommons.sif.io.Writer sifWriter;
  private SifgraphProperties props;

  private final RelationTypeEnum[] DEFAULT_RELS;

  @Autowired
  private ResourceLoader resourceLoader;

  @Autowired
  public Controller(SifgraphProperties properties) {
    this.props = properties;
    DEFAULT_RELS = properties.getRelationships();
  }

  @PostConstruct
  void init() throws IOException {
    final EdgeAnnotationType[] annotationTypes = props.getAnnotations();
    sifWriter = new org.pathwaycommons.sif.io.Writer(false, annotationTypes);
    InputStream is = new GZIPInputStream(resourceLoader.getResource(props.getData()).getInputStream());
    graph = new Loader(annotationTypes).load(is);
    is.close();
  }

  @RequestMapping(path = "/neighborhood")
  @ApiOperation(
    value = "NEIGHBORHOOD",
    notes = "Given gene names (source), finds the neighborhood sub-network in the " +
      "Pathway Commons Simple Interaction Format (extened SIF) graph " +
      "(see http://www.pathwaycommons.org/pc2/formats#sif)"
  )
  public String nhood(
    @ApiParam("Graph traversal direction. Use UNDIRECTED if you want to see interacts-with relationships too.")
    @RequestParam(required = false, defaultValue = "BOTHSTREAM") Direction direction,
    @ApiParam("Graph traversal depth. Limit > 1 value can result in very large data or error.")
    @RequestParam(required = false, defaultValue = "1") Integer limit,
    @ApiParam("Set of gene identifiers (HGNC Symbol) - 'seeds' for the graph traversal algorithm.")
    @RequestParam(required = true) String[] source,
    @ApiParam("Filter by binary relationship (SIF edge) type(s)")
    @RequestParam(required = false) RelationTypeEnum[] pattern
  ) {
    Set<String> sources = new HashSet();
    for (String s : source)
      sources.add(s);

    pattern = (pattern != null && pattern.length > 0) ? pattern : DEFAULT_RELS;
    try {
      Set<Object> result = QueryExecutor.searchNeighborhood(graph,
        new RelationTypeSelector(pattern), sources, direction, limit);

      return write(result);
    } catch (Exception e) {
      throw new RuntimeException("/neighborhood failed: " + e);
    }
  }

  @RequestMapping(path = "/pathsbetween")
  @ApiOperation(
    value = "PATHS-BETWEEN",
    notes = "Given gene names (sources), finds the paths between them; extracts a sub-network from the " +
      "Pathway Commons SIF graph."
  )
  public String pathsbetween(
    @ApiParam("Directionality: 'true' is for DOWNSTREAM/UPSTREAM, 'false' - UNDIRECTED")
    @RequestParam(required = false, defaultValue = "false") Boolean directed,
    @ApiParam("Graph traversal depth. Limit > 3 can result in very large data or error.")
    @RequestParam(required = false, defaultValue = "1") Integer limit,
    @ApiParam("A set of gene identifiers.")
    @RequestParam(required = true) String[] source,
    @ApiParam("Filter by binary relationship (SIF edge) type(s)")
    @RequestParam(required = false) RelationTypeEnum[] pattern
  ) {
    Set<String> sources = new HashSet();
    for (String s : source)
      sources.add(s);

    try {
      Set<Object> result = QueryExecutor.searchPathsBetween(graph,
        new RelationTypeSelector((pattern != null && pattern.length > 0) ? pattern : DEFAULT_RELS),
        sources, directed, limit);

      return write(result);
    } catch (Exception e) {
      throw new RuntimeException("/pathsbetween failed: " + e);
    }
  }

  @RequestMapping(path = "/commonstream")
  @ApiOperation(
    value = "COMMON-STREAM",
    notes = "Given gene symbols (sources), finds the common stream for them; " +
      "extracts a sub-network from the loaded Pathway Commons SIF model."
  )
  public String commonstream(
    @ApiParam("Graph traversal direction. Use either DOWNSTREAM or UPSTREAM only.")
    @RequestParam(required = false, defaultValue = "DOWNSTREAM") Direction direction,
    @ApiParam("Graph traversal depth.")
    @RequestParam(defaultValue = "1") Integer limit,
    @ApiParam("A set of gene identifiers.")
    @RequestParam(required = true) String[] source,
    @ApiParam("Filter by binary relationship (SIF edge) type(s)")
    @RequestParam(required = false) RelationTypeEnum[] pattern
  ) {
    Set<String> sources = new HashSet();
    for (String s : source) sources.add(s);

    try {
      Set<Object> result = QueryExecutor.searchCommonStream(graph,
        new RelationTypeSelector((pattern != null && pattern.length > 0) ? pattern : DEFAULT_RELS),
        sources, direction, limit);

      return write(result);
    } catch (Exception e) {
      throw new RuntimeException("/commonstream failed: " + e);
    }
  }

  @RequestMapping(path = "/pathsfromto")
  @ApiOperation(
    value = "PATHS-FROM-TO",
    notes = "Given source and target (optional) gene symbols, finds the paths from sources to targets " +
      "(or between sources if targets are not present); extracts that sub-network from the loaded graph."
  )
  public String pathsfromto(
    @ApiParam("Graph traversal depth. Limit > 2 can result in very large data or error.")
    @RequestParam(required = false, defaultValue = "1") Integer limit,
    @ApiParam("A source set of gene identifiers.")
    @RequestParam(required = true) String[] source,
    @ApiParam("A target set of gene identifiers.")
    @RequestParam(required = true) String[] target,
    @ApiParam("Filter by binary relationship (SIF edge) type(s)")
    @RequestParam(required = false) RelationTypeEnum[] pattern
  ) {
    Set<String> sources = new HashSet();
    for (String s : source) sources.add(s);

    Set<String> targets = new HashSet();
    for (String s : target) targets.add(s);

    try {
      Set<Object> result = QueryExecutor.searchPathsFromTo(graph,
        new RelationTypeSelector((pattern != null && pattern.length > 0) ? pattern : DEFAULT_RELS),
        sources, targets, limit);

      return write(result);
    } catch (Exception e) {
      throw new RuntimeException("/pathsfromto failed: " + e);
    }
  }

  private String write(Set<Object> result) throws IOException {
    OutputStream bos = new ByteArrayOutputStream();
    sifWriter.write(result, bos);
    return bos.toString();
  }

}
