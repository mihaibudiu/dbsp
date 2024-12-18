from .aggtst_base import TstView


class aggtst_row_max_value(TstView):
    def __init__(self):
        # checked manually
        self.data = [{"c1": {"expr$0": 7, "expr$1": "hi", "expr$2": "hiya"}}]
        self.sql = """CREATE MATERIALIZED VIEW row_max AS SELECT
                      MAX(ROW(c1, c2, c3)) AS c1
                      FROM row_tbl"""


class aggtst_row_max_gby(TstView):
    def __init__(self):
        # checked manually
        self.data = [
            {
                "id": 0,
                "c1": {"expr$0": 4, "expr$1": None, "expr$2": "adios"},
                "c2": {"expr$0": "ola", "expr$1": 3, "expr$2": "ciao"},
            },
            {
                "id": 1,
                "c1": {"expr$0": 7, "expr$1": "hi", "expr$2": "hiya"},
                "c2": {"expr$0": "hi", "expr$1": 7, "expr$2": "hiya"},
            },
        ]
        self.sql = """CREATE MATERIALIZED VIEW row_max_gby AS SELECT
                      id,
                      MAX(ROW(c1, c2, c3)) AS c1,
                      MAX(ROW(c2, c1, c3)) AS c2
                      FROM row_tbl
                      GROUP BY id"""


class aggtst_row_max_distinct(TstView):
    def __init__(self):
        # checked manually
        self.data = [{"c1": {"expr$0": 7, "expr$1": "hi", "expr$2": "hiya"}}]
        self.sql = """CREATE MATERIALIZED VIEW row_max_distinct AS SELECT
                      MAX(DISTINCT ROW(c1, c2, c3)) AS c1
                      FROM row_tbl"""


class aggtst_row_max_distinct_gby(TstView):
    def __init__(self):
        # checked manually
        self.data = [
            {"id": 0, "c1": {"expr$0": 4, "expr$1": None, "expr$2": "adios"}},
            {"id": 1, "c1": {"expr$0": 7, "expr$1": "hi", "expr$2": "hiya"}},
        ]
        self.sql = """CREATE MATERIALIZED VIEW row_max_distinct_gby AS SELECT
                      id,
                      MAX(DISTINCT ROW(c1, c2, c3)) AS c1
                      FROM row_tbl
                      GROUP BY id"""


class aggtst_row_max_where(TstView):
    def __init__(self):
        # checked manually
        self.data = [{"c1": {"expr$0": 7, "expr$1": "hi", "expr$2": "hiya"}}]
        self.sql = """CREATE MATERIALIZED VIEW row_max_where AS SELECT
                      MAX(ROW(c1, c2, c3)) FILTER(WHERE c2 < c3) AS c1
                      FROM row_tbl"""


class aggtst_row_max_where_gby(TstView):
    def __init__(self):
        # checked manually
        self.data = [
            {"id": 0, "c1": None},
            {"id": 1, "c1": {"expr$0": 7, "expr$1": "hi", "expr$2": "hiya"}},
        ]
        self.sql = """CREATE MATERIALIZED VIEW row_max_where_gby AS SELECT
                      id, MAX(ROW(c1, c2, c3)) FILTER(WHERE c2 < c3) AS c1
                      FROM row_tbl
                      GROUP BY id"""