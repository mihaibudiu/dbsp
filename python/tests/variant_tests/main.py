## Add here import statements for all files with tests

from tests.aggregate_tests.aggtst_base import *  # noqa: F403
from tests.aggregate_tests.atest_run import run  # noqa: F403
from tests.variant_tests.dtype_and_variant import *  # noqa: F403
from tests.variant_tests.cpmx_variant import *  # noqa: F403
from tests.variant_tests.arr_of_cmpx_type import *  # noqa: F403
from tests.variant_tests.row_of_cmpx_type import *  # noqa: F403
from tests.variant_tests.udt_of_cmpx_type import *  # noqa: F403


def main():
    run("varnttst_", "variant_tests")


if __name__ == "__main__":
    main()
