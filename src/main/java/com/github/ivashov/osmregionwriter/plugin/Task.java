package com.github.ivashov.osmregionwriter.plugin;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.*;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

public class Task implements Sink {
    private static final Logger log = Logger.getLogger(Task.class.getName());

    private boolean isRelationsStarted = false;

    private String directory;

    private int count;

    private Tag tagLevel = new Tag("admin_level", "6");
    private Tag tagAdmin = new Tag("boundary", "administrative");

    private HashMap<Long, NodeDesc> nodes = new HashMap<>();
    private HashMap<Long, WayDesc> ways = new HashMap<>();

    private List<Segment> segments = new ArrayList<>();

    private List<Region> regions = new ArrayList<>();

    public Task(String directory) {
        this.directory = directory;
    }

    @Override
    public void process(EntityContainer entityContainer) {
        Entity entity = entityContainer.getEntity();

        if (entity.getType() == EntityType.Relation) {
            isRelationsStarted = true;
            Relation relation = (Relation) entity;
            Collection<Tag> tags = entity.getTags();

            String name = null;
            int found = 0;

            for (Tag tag : tags) {
                if (tag.getKey().equals("boundary") && tag.getValue().equals("administrative")) {
                    found++;
                } else if (tag.getKey().equals("admin_level") && tag.getValue().equals("6")) {
                    found++;
                } else if (tag.getKey().equals("name")) {
                    name = tag.getValue();
                }
            }

            if (found == 2) {
                if (name != null) {
                    log.info("Relation found");

                    Region region;
                    try {
                        region = new Region(name, relation);
                        regions.add(region);
                    } catch (Exception e) {
                        log.warning("Region " + name + " incomplete");
                    }
                }
            }
        } else if (entity.getType() == EntityType.Node) {
            if (isRelationsStarted) {
                throw new IllegalStateException("Input must be sorted in order relations after nodes and ways");
            }
            Node node = (Node) entity;
            nodes.put(node.getId(), new NodeDesc(node));
        } else if (entity.getType() == EntityType.Way) {
            if (isRelationsStarted) {
                throw new IllegalStateException("Input must be sorted in order relations after nodes and ways");
            }
            Way way = (Way) entity;
            ways.put(way.getId(), new WayDesc(way));
        }
    }

    @Override
    public void initialize(Map<String, Object> metaData) {
        new JSONArray();
    }

    @Override
    public void complete() {
        File outputDirectory = new File(directory);
        JSONObject regionsJson = new JSONObject();
        regionsJson.put("version", 1);

        JSONArray regionsArray = new JSONArray();

        for (Region region : regions) {
            File outFile = new File(outputDirectory, region.regionId + ".poly");
            try {
                region.writePoly(outFile);
                region.writeMeta(regionsArray);
            } catch (FileNotFoundException e) {
                log.warning("Can't write .poly file for " + region.name);
            }
        }

        regionsJson.put("regions", regionsArray);

        try {
            FileWriter writer = new FileWriter(new File(outputDirectory, "regions.json"));
            writer.write(regionsJson.toString());
            writer.close();
        } catch (IOException e) {
            log.warning("Can't write region.json file");
        }
    }


    @Override
    public void release() {
    }

    private static class Segment {
        ArrayDeque<Long> nodes;

        boolean addSegment(Segment other) {
            long first = nodes.peekFirst();
            long last = nodes.peekLast();

            long oFirst = other.nodes.peekFirst();
            long oLast = other.nodes.peekLast();

            if (first == oFirst) {
                while (!other.nodes.isEmpty()) {
                    nodes.addFirst(other.nodes.pollFirst());
                }
            } else if (last == oLast) {
                while (!other.nodes.isEmpty()) {
                    nodes.addLast(other.nodes.pollLast());
                }
            } else if (last == oFirst) {
                while (!other.nodes.isEmpty()) {
                    nodes.addLast(other.nodes.pollFirst());
                }
            } else if (first == oLast) {
                while (!other.nodes.isEmpty()) {
                    nodes.addFirst(other.nodes.pollLast());
                }
            } else {
                return false;
            }

            return true;
        }
    }

    private static class NodeDesc {
        double lat, lon;

        public NodeDesc(Node node) {
            lat = node.getLatitude();
            lon = node.getLongitude();
        }
    }

    private static class WayDesc {
        private long[] nodes;

        public WayDesc(Way way) {
            nodes = new long[way.getWayNodes().size()];
            nodes = way.getWayNodes().stream().mapToLong(WayNode::getNodeId).toArray();
        }
    }

    private class Region {
        private final String name;
        private final String regionId;

        private final List<Segment> segments = new ArrayList<>();

        public Region(String name, Relation relation) throws Exception {
            this.name = name;
            this.regionId = String.valueOf(name.hashCode());
            List<RelationMember> members = relation.getMembers();
            for (RelationMember relationMember : members) {
                if (relationMember.getMemberType() == EntityType.Way) {
                    WayDesc wayDesc = ways.get(relationMember.getMemberId());
                    if (wayDesc != null) {
                        addWay(wayDesc.nodes);
                    } else {
                        throw new Exception("Incomplete region");
                    }
                }
            }

        }

        public void addWay(long[] nodes) {
            Segment newSegment = new Segment();
            newSegment.nodes = new ArrayDeque<>(nodes.length);
            for (long node : nodes) {
                newSegment.nodes.addLast(node);
            }

            for (Iterator<Segment> iterator = segments.iterator(); iterator.hasNext(); ) {
                Segment segment = iterator.next();

                if (newSegment.addSegment(segment)) {
                    iterator.remove();
                }
            }

            segments.add(newSegment);
        }

        public void writePoly(File outFile) throws FileNotFoundException {
            PrintWriter writer = new PrintWriter(outFile);
            writer.println(name);

            int c = 0;
            for (Segment segment : segments) {
                writer.println("" + ++c);

                for (Long node : segment.nodes) {
                    NodeDesc nodeDesc = nodes.get(node);
                    if (nodeDesc != null) {
                        writer.println(nodeDesc.lon + " " + nodeDesc.lat);
                    } else {
                        log.warning("Node for " + node + " not found. Region " + name);
                    }
                }

                writer.println("END");
            }
            writer.println("END");
            writer.close();
        }

        public void writeMeta(JSONArray regionArray) {
            JSONObject regionJson = new JSONObject();

            regionJson.put("region-id", regionId);
            regionJson.put("admin-level", 6);
            regionJson.put("poly-file", regionId + ".poly");

            JSONObject namesJson = new JSONObject();
            namesJson.put("ru", name);

            regionJson.put("names", namesJson);

            regionArray.put(regionJson);
        }
    }
}
